/*
 * MIT License
 *
 * Copyright (c) 2017-2019 RiiConnect24 and its contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package xyz.rc24.bot.commands.wii;

import ch.qos.logback.classic.Logger;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import xyz.rc24.bot.Bot;
import xyz.rc24.bot.RiiConnect24Bot;
import xyz.rc24.bot.commands.Categories;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up errors using the Wiimmfi API.
 *
 * @author Spotlight, Artuto
 */

public class ErrorInfoCmd extends Command
{
    private final boolean debug;
    private final OkHttpClient httpClient;
	
	private final Pattern CHANNEL = Pattern.compile("(NEWS|FORE)0{4}\\d{2}", Pattern.CASE_INSENSITIVE);
	private final Pattern CHANNEL_CODE = Pattern.compile("0{4}\\d{2}");
	
	private final Pattern CODE = Pattern.compile("\\d{1,6}");

	private final Gson gson = new Gson();
	private final Logger logger = RiiConnect24Bot.getLogger(ErrorInfoCmd.class);

    public ErrorInfoCmd(Bot bot)
    {
        this.debug = bot.getConfig().isDebug();
        this.httpClient = bot.getHttpClient();
        this.name = "error";
        this.help = "Looks up errors using the Wiimmfi API.";
        this.category = Categories.WII;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
        this.guildOnly = false;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        Matcher channelCheck = CHANNEL.matcher(event.getArgs());

        // Check for Fore/News
        if(channelCheck.find())
        {
            // First match will be the type, then second our actual code.
            int code;
            try
            {
                // Make sure the code's actually a code.
                Matcher codeCheck = CHANNEL_CODE.matcher(channelCheck.group());
                if(!(codeCheck.find()))
					throw new NumberFormatException();

                code = Integer.parseInt(codeCheck.group(0));
                if(channelErrors.get(code) == null)
					throw new NumberFormatException();
            }
            catch(NumberFormatException ignored)
            {
                event.replyError("Could not find the specified app error code.");
                return;
            }

            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("Here's information about your error:");
            builder.setDescription(channelErrors.get(code));
            builder.setColor(Color.decode("#D32F2F"));
            builder.setFooter("All information provided by RC24 Developers.", null);
            event.reply(builder.build());
        }
        else
        {
            int code;
            try
            {
                // Validate if it is a number.
                Matcher codeCheck = CODE.matcher(event.getArgs());
                if(!(codeCheck.find()))
					throw new NumberFormatException();
				
                code = Integer.parseInt(codeCheck.group(0));
                if(code == 0)
				{
                    // 0 returns an empty array (see https://forum.wii-homebrew.com/index.php/Thread/57051-Wiimmfi-Error-API-has-an-error/?postID=680936)
                    // We'll just treat it as an error.
                    throw new NumberFormatException();
				}
            }
            catch(NumberFormatException ignored)
            {
                event.replyError("Enter a valid error code!");
                return;
            }

            // Get method
            String method = (debug ? "t=" : "e=") + code;
            String url = "https://wiimmfi.de/error?" + method + "&m=json";

            if(debug)
                logger.info("Sending request to '{}'", url);

            Request request = new Request.Builder().url(url).build();
            httpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e)
                {
                    event.replyError("Hm, something went wrong on our end. Ask a dev to check out my console.");
                    logger.error("Something went wrong whilst checking error code '" + code +
                            "' with Wiimmfi: {}", e.getMessage(), e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response)
                {
                    if(!(response.isSuccessful()))
                    {
                        onFailure(call, new IOException("Not success response code: " + response.code()));
                        return;
                    }

                    success(event, response);
                }
            });
        }
    }

    @SuppressWarnings("ConstantConditions") // Response body can't be null at this point of the execution
    private void success(@NotNull CommandEvent event, @NotNull Response response)
    {
        JSONFormat json = gson.fromJson(new InputStreamReader(response.body().byteStream()), JSONFormat[].class)[0];
        if(!(json.found == 1))
        {
            event.replyError("Could not find the specified error from Wiimmfi.");
            return;
        }

        StringBuilder infoBuilder = new StringBuilder();
        for(InfoListFormat format : json.infolists)
        {
            String htmlToMarkdown = format.info;
            Document infoSegment = Jsoup.parseBodyFragment(htmlToMarkdown);

            // Replace links with markdown format
            for(Element hRef : infoSegment.select("a[href]"))
            {
                // So, we have to transform &amp; back to &.
                // It's funny, the same issue happened with Nokogiri and Ruby.
                String realOuterHTML = hRef.outerHtml();
                realOuterHTML = realOuterHTML.replace("&amp;", "&");
                htmlToMarkdown = htmlToMarkdown.replace(realOuterHTML, "[" + hRef.text() + "](" + hRef.attr("href") + ")");
            }

            // Parse again to handle updates
            infoSegment = Jsoup.parseBodyFragment(htmlToMarkdown);
            for(Element bold : infoSegment.select("b"))
                htmlToMarkdown = htmlToMarkdown.replace(bold.outerHtml(), "**" + bold.text() + "**");

            // ...and parse, once more.
            infoSegment = Jsoup.parseBodyFragment(htmlToMarkdown);
            for(Element italics : infoSegment.select("i"))
                htmlToMarkdown = htmlToMarkdown.replace(italics.outerHtml(), "*" + italics.text() + "*");

            infoBuilder.append(format.type).append(" for error ").append(format.name).append(": ").append(htmlToMarkdown).append("\n");
        }

        // Check for dev note
        if(codeNotes.containsKey(json.error))
            infoBuilder.append("Note from RiiConnect24: ").append(codeNotes.get(json.error));

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Here's information about your error:");
        builder.setDescription(infoBuilder.toString());
        builder.setColor(Color.decode("#D32F2F"));
        builder.setFooter("All information is from Wiimmfi unless noted.", null);

        event.reply(builder.build());
        response.close();
    }

    private class JSONFormat
    {
        @SerializedName("error")
        int error;
        @SerializedName("found")
        int found;
        @SerializedName("infolist")
        InfoListFormat[] infolists;
    }

    private class InfoListFormat
    {
        @SerializedName("type")
        String type;
        @SerializedName("name")
        String name;
        @SerializedName("info")
        String info;
    }

    private final Map<Integer, String> channelErrors = new HashMap<Integer, String>()
    {{
        put(1, "Can't open the VFF Follow https://wii.guide/riiconnect24-troubleshooting to fix it.");
        put(2, "WiiConnect24 file problem.");
        put(3, "VFF file corrupted. Follow https://wii.guide/riiconnect24-troubleshooting to fix it.");
        put(4, "Unknown (it probably doesn't exist).");
        put(5, "VFF processing error. Follow https://wii.guide/riiconnect24-troubleshooting to fix it.");
        put(6, "Invalid data. If getting this on the **Forecast Channel**, try again in a few minutes. " +
                "If you're still getting this error, follow https://wii.guide/riiconnect24-batteryfix " +
                "and it might fix it.");
        put(99, "Other error. Follow https://wii.guide/riiconnect24-troubleshooting to potentially fix it.");
    }};

    private final Map<Integer, String> codeNotes = new HashMap<Integer, String>()
    {{
        put(101409, "If you are getting this problem while doing something with Wii Mail, check if you patched the nwc24msg.cfg correctly. https://bit.ly/2QUrsyD");
        put(102032, "The IOS the app/game uses is not patched for RiiConnect24. Try sending a message again but do it quickly, you need to do it in less than a minute. We hope to improve Wii Mail to stop getting this error.");
        put(102409, "If you are getting this problem while doing something with Wii Mail, check if you patched the nwc24msg.cfg correctly. https://bit.ly/2QUrsyD");
        put(103409, "If you are getting this problem while doing something with Wii Mail, check if you patched the nwc24msg.cfg correctly. https://bit.ly/2QUrsyD");
        put(104409, "If you are getting this problem while doing something with Wii Mail, check if you patched the nwc24msg.cfg correctly. https://bit.ly/2QUrsyD");
        put(105409, "If you are getting this problem while doing something with Wii Mail, check if you patched the nwc24msg.cfg correctly. https://bit.ly/2QUrsyD");
        put(107006, "Are you getting this on the News Channel? If so, please tell Larsenv you're getting this error and tell him your country and language your Wii is set to.");
        put(107245, "Your IOS probably aren't patched. Go to https://wii.guide/riiconnect24 for instructions on how to patch them.");
        put(107304, "Try again, or maybe play with your Internet settings (change your secondary DNS to 164.132.44.106 as a test). This may not be easy to fix. If you still are having problems, try making requests to wapp.wii.com redirect to 164.132.44.106 on your router.");
        put(107305, "Try again. If it still doesn't work, it might be a problem with your Internet or RiiConnect24's servers.");
        put(110211, "If you're getting this, tell Larsenv or KcrPL your Wii Number and they will delete it from the database so you can reregister with the mail patcher.");
        put(110220, "Looks like the password your Wii uses isn't matching the one on the server. If you're getting this, tell Larsenv or KcrPL your Wii Number and they will delete it from the database so you can reregister with the mail patcher.");
        put(110230, "Looks like the password your Wii uses isn't matching the one on the server. If you're getting this, tell Larsenv or KcrPL your Wii Number and they will delete it from the database so you can reregister with the mail patcher.");
        put(110240, "Looks like the password your Wii uses isn't matching the one on the server. If you're getting this, tell Larsenv or KcrPL your Wii Number and they will delete it from the database so you can reregister with the mail patcher.");
        put(110250, "Looks like the password your Wii uses isn't matching the one on the server. If you're getting this, tell Larsenv or KcrPL your Wii Number and they will delete it from the database so you can reregister with the mail patcher.");
        put(117403, "This is a 403 Forbidden error. If you're getting this, tell Larsenv where you're getting this error on.");
        put(117404, "This is a 404 Not Found error. If you're getting this on the Everybody Votes Channel, what questions does this appear on?");
        put(117500, "This is a 500 Internal Server error. If you're getting this, tell Larsenv where you're getting this error on.");
        put(117503, "This is a 503 Service Unavailable error. If you're getting this, tell Larsenv where you're getting this error on.");
        put(20103, "Delete DWC_AUTHDATA file stored in nand:/shared2/ using WiiXplorer.");
        put(231000, "Restart the Channel or your Wii then try again. We hope to fix this error from happening in the future, sorry for the inconvenience!");
        put(231401, "You are not using the patched WAD for the Everybody Votes Channel. Please follow this tutorial: https://wii.guide/riiconnect24-evc");
        put(231409, "You are not using the patched WAD for the Everybody Votes Channel. Please follow this tutorial: https://wii.guide/riiconnect24-evc");
        put(239001, "Your IOS probably aren't patched. Go to https://wii.guide/riiconnect24 for instructions on how to patch them. Occasionally, this error can mean it downloaded invalid data.");
        put(51330, "Try the suggestions found on Nintendo's site: https://bit.ly/2OoC0c2");
        put(51331, "Try the suggestions found on Nintendo's site: https://bit.ly/2OoC0c2");
        put(51332, "Try the suggestions found on Nintendo's site: https://bit.ly/2OoC0c2");
    }};
}

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

package xyz.rc24.bot.commands.tools;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.rc24.bot.Const;
import xyz.rc24.bot.commands.Categories;

import java.awt.Color;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class StatsCmd extends Command
{
    private Logger LOG = LoggerFactory.getLogger("Stats Command");
    private String STATS_URL = "http://164.132.44.106/stats.json";

    public StatsCmd()
    {
        this.name = "stats";
        this.help = "Shows stats for the RC24 services";
        this.category = Categories.TOOLS;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
        this.ownerCommand = false;
        this.guildOnly = true;
    }

    @Override
    protected void execute(CommandEvent event)
    {
        OkHttpClient client = ((JDAImpl) event.getJDA()).getHttpClient();
        Request request = new Request.Builder().url(STATS_URL).addHeader("User-Agent", "RC24-Bot " + Const.VERSION).build();

        try(Response response = client.newCall(request).execute())
        {
            EmbedBuilder eb = new EmbedBuilder();
            MessageBuilder mb = new MessageBuilder();

            eb.setDescription(parseJSON(response));
            eb.setColor(Color.decode("#29B7EB"));

            mb.setContent("<:RC24:302470872201953280> Service statuses of RC24:").setEmbed(eb.build());
            event.reply(mb.build());
        }
        catch(IOException e)
        {
            event.replyError("Could not contact the Stats API! Please ask a owner to check the console.");
            LOG.error("Exception while contacting the Stats API! ", e);
        }
    }

    private String parseJSON(Response response)
    {
        JSONObject json = new JSONObject(new JSONTokener(response.body().byteStream()));
        Set<String> keys = new TreeSet<>(json.keySet());

        StringBuilder green = new StringBuilder("```diff\n");
        StringBuilder yellow = new StringBuilder("```fix\n");
        StringBuilder red = new StringBuilder("```diff\n");
        StringBuilder sb = new StringBuilder();

        keys.forEach(k ->
        {
            String status = json.getString(k);

            switch(status)
            {
                case "green":
                    green.append("+ ").append(k).append("\n");
                    break;
                case "yellow":
                    yellow.append("* ").append(k).append("\n");
                    break;
                default:
                    red.append("- ").append(k).append("\n");
            }
        });

        sb.append("Supported by RiiConnect24:\n").append(green).append("```\nIn progress...\n").append(yellow).append("```\nNot supported:\n").append(red).append("```");

        return sb.toString();
    }
}

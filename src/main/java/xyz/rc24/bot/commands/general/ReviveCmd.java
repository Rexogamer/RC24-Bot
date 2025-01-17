package xyz.rc24.bot.commands.general;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import xyz.rc24.bot.Bot;
import xyz.rc24.bot.commands.Categories;
import xyz.rc24.bot.core.entities.Poll;
import xyz.rc24.bot.managers.PollManager;

public class ReviveCmd extends Command
{
    private final PollManager manager;

    public ReviveCmd(Bot bot)
    {
        this.name = "revive";
        this.help = "Revives the chat by sending a EVC poll for users to vote in.";
        this.category = Categories.GENERAL;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
        this.manager = bot.getPollManager();
    }

    @Override
    protected void execute(CommandEvent event)
    {
        // Get a random poll
        Poll poll = manager.getRandomPoll();

        // Now we need to build the embed
        EmbedBuilder embed = new EmbedBuilder()
        {{
            setTitle("<:EverybodyVotesChannel:317090360449040388> " + poll.getQuestion());
            setDescription("\uD83C\uDD70 " + poll.getResponse1() + "\n" +
                    "_ _\n" + // Line separator
                    "\uD83C\uDD71 " + poll.getResponse2());
            addField("Users who reacted  \uD83C\uDD70:", "", false);
            addField("Users who reacted \uD83C\uDD71:", "", false);
            setColor(event.getSelfMember().getColor());
            setFooter("This question was in the " + poll.getCountryFlag() + " EVC", null);
        }};

        // Send embed to chat
        event.reply(embed.build(), s ->
        {
            // Add message ID to tracked list
            manager.trackId(s.getIdLong());

            // Add reactions
            s.addReaction("\uD83C\uDD70").queue();
            s.addReaction("\uD83C\uDD71").queue();
        });
    }
}

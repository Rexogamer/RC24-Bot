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

package xyz.rc24.bot;

import ch.qos.logback.classic.Logger;
import co.aikar.idb.DB;
import co.aikar.idb.DatabaseOptions;
import co.aikar.idb.PooledDatabaseOptions;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.mysql.cj.jdbc.Driver;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import io.sentry.Sentry;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import okhttp3.OkHttpClient;
import xyz.rc24.bot.commands.botadm.Bash;
import xyz.rc24.bot.commands.botadm.Eval;
import xyz.rc24.bot.commands.botadm.Shutdown;
import xyz.rc24.bot.commands.general.BirthdayCmd;
import xyz.rc24.bot.commands.general.FlagCmd;
import xyz.rc24.bot.commands.general.InviteCmd;
import xyz.rc24.bot.commands.general.PingCmd;
import xyz.rc24.bot.commands.general.ReviveCmd;
import xyz.rc24.bot.commands.general.SetBirthdayCmd;
import xyz.rc24.bot.commands.tools.MailParseListener;
import xyz.rc24.bot.commands.tools.MailPatchCmd;
import xyz.rc24.bot.commands.tools.PrefixCmd;
import xyz.rc24.bot.commands.tools.ServerSettingsCmd;
import xyz.rc24.bot.commands.tools.StatsCmd;
import xyz.rc24.bot.commands.wii.AddCmd;
import xyz.rc24.bot.commands.wii.BlocksCmd;
import xyz.rc24.bot.commands.wii.CodeCmd;
import xyz.rc24.bot.commands.wii.DNS;
import xyz.rc24.bot.commands.wii.ErrorInfoCmd;
import xyz.rc24.bot.commands.wii.WadsCmd;
import xyz.rc24.bot.commands.wii.WiiWare;
import xyz.rc24.bot.core.BotCore;
import xyz.rc24.bot.core.entities.impl.BotCoreImpl;
import xyz.rc24.bot.database.BirthdayDataManager;
import xyz.rc24.bot.database.CodeDataManager;
import xyz.rc24.bot.database.Database;
import xyz.rc24.bot.database.GuildSettingsDataManager;
import xyz.rc24.bot.database.MorpherDataManager;
import xyz.rc24.bot.listeners.DataDogStatsListener;
import xyz.rc24.bot.listeners.Morpher;
import xyz.rc24.bot.listeners.PollListener;
import xyz.rc24.bot.listeners.ServerLog;
import xyz.rc24.bot.managers.BirthdayManager;
import xyz.rc24.bot.managers.PollManager;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Add all commands, and start all listeners.
 *
 * @author Spotlight and Artuto
 */

@SuppressWarnings({"unused", "WeakerAccess"})
public class Bot extends ListenerAdapter
{
    public BotCore core;
    public Config config;
    public EventWaiter waiter;
    public JDA jda;

    // Database & Data managers
    private Database db;
    private BirthdayDataManager birthdayDataManager;
    private CodeDataManager codeDataManager;
    private GuildSettingsDataManager guildSettingsDataManager;
    private MorpherDataManager morpherDataManager;

    // Managers
    private BirthdayManager birthdayManager;
    private PollManager pollManager;

    private final Logger logger = RiiConnect24Bot.getLogger();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ScheduledExecutorService botScheduler = new ScheduledThreadPoolExecutor(1);
    private final ScheduledExecutorService birthdaysScheduler = new ScheduledThreadPoolExecutor(1);
    private final ScheduledExecutorService musicNightScheduler = new ScheduledThreadPoolExecutor(1);

    void run() throws LoginException, IOException
    {
        RiiConnect24Bot.setInstance(this);
        this.config = new Config();
        this.core = new BotCoreImpl(this);
        this.waiter = new EventWaiter();

        // Start database
        this.db = initDatabase();
        this.birthdayDataManager = new BirthdayDataManager(db);
        this.codeDataManager = new CodeDataManager(this);
        this.guildSettingsDataManager = new GuildSettingsDataManager(this);
        this.morpherDataManager = new MorpherDataManager(db);

        // Start managers
        this.birthdayManager = new BirthdayManager(getBirthdayDataManager());
        this.pollManager = new PollManager();

        // Start Sentry (if enabled)
        if(config.isSentryEnabled() && !(config.getSentryDSN().isEmpty()))
            Sentry.init(config.getSentryDSN());

        DataDogStatsListener dataDogStatsListener = null;

        if(config.isDatadogEnabled())
        {
            StatsDClient statsd = new NonBlockingStatsDClient(config.getDatadogPrefix(), config.getDatadogHost(), config.getDatadogPort());

            dataDogStatsListener = new DataDogStatsListener(statsd);
        }

        // Convert List<Long> of secondary owners to String[] so we can set later
        List<Long> owners = config.getSecondaryOwners();
        String[] coOwners = new String[owners.size()];

        for(int i = 0; i < owners.size(); i++)
            coOwners[i] = String.valueOf(owners.get(i));

        // Setup Command Client
        DataDogStatsListener finalDataDogStatsListener = dataDogStatsListener;
        CommandClientBuilder client = new CommandClientBuilder()
        {{
            setGame(Game.playing(config.getPlaying()));
            setStatus(config.getStatus());
            setEmojis(Const.SUCCESS_E, Const.WARN_E, Const.ERROR_E);
            setLinkedCacheSize(40);
            setOwnerId(String.valueOf(config.getPrimaryOwner()));
            setPrefix("@mention");
            setServerInvite("https://discord.gg/5rw6Tur");
            setGuildSettingsManager(getGuildSettingsDataManager());
            setCoOwnerIds(coOwners);
            addCommands(
                    // Bot administration
                    new Bash(), new Eval(Bot.this), new Shutdown(),

                    // General
                    new BirthdayCmd(Bot.this), new FlagCmd(Bot.this), new InviteCmd(),
                    new ReviveCmd(Bot.this), new PingCmd(), new SetBirthdayCmd(Bot.this),

                    // Tools
                    new MailPatchCmd(config), new PrefixCmd(getGuildSettingsDataManager()),
                    new ServerSettingsCmd(Bot.this), new StatsCmd(),

                    // Wii-related
                    new AddCmd(Bot.this), new CodeCmd(Bot.this), new BlocksCmd(),
                    new ErrorInfoCmd(Bot.this), new DNS(), new WadsCmd(), new WiiWare());

            if(!(finalDataDogStatsListener == null))
                setListener(finalDataDogStatsListener);
        }};

        // JDA Connection
        JDABuilder builder = new JDABuilder(config.getToken())
                .setStatus(OnlineStatus.DO_NOT_DISTURB)
                .setGame(Game.playing("Loading..."))
                .addEventListener(this, client.build(), waiter, new ServerLog(this), new MailParseListener(this),
                        new PollListener(getPollManager()))
                .setAudioEnabled(false);

        if(config.isMorpherEnabled())
            builder.addEventListener(new Morpher(config, getMorpherDataManager()));
        if(!(dataDogStatsListener == null))
            builder.addEventListener(dataDogStatsListener);

        builder.build();
    }

    @Override
    public void onReady(ReadyEvent event)
    {
        this.jda = event.getJDA();
        logger.info("Done loading!");

        // Check if we need to set a game
        if(config.getPlaying().isEmpty())
            event.getJDA().getPresence().setGame(Game.playing("Type " + config.getPrefix() + "help"));

        ZonedDateTime zonedNow = OffsetDateTime.now().toZonedDateTime();
        ZonedDateTime zonedNext;

        if(config.birthdaysAreEnabled())
        {
            // Every day at 8AM
            zonedNext = zonedNow.withHour(8).withMinute(0).withSecond(0);
            if(zonedNow.compareTo(zonedNext) > 0)
                zonedNext = zonedNext.plusDays(1);

            Duration duration = Duration.between(zonedNow, zonedNext);
            long initialDelay = duration.getSeconds();

            birthdaysScheduler.scheduleWithFixedDelay(() -> getBirthdayManager().updateBirthdays(event.getJDA(), config.getBirthdayChannel()), initialDelay, 86400, TimeUnit.SECONDS);
        }

        if(config.isMusicNightReminderEnabled())
        {
            zonedNext = zonedNow.withHour(19).withMinute(45).withSecond(0);
            if(zonedNow.compareTo(zonedNext) > 0)
                zonedNext = zonedNext.plusDays(1);

            Duration duration = Duration.between(zonedNow, zonedNext);
            long initialDelay = duration.getSeconds();

            musicNightScheduler.scheduleWithFixedDelay(() -> remindMusicNight(event.getJDA()), initialDelay, 86400, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onShutdown(ShutdownEvent event)
    {
        birthdaysScheduler.shutdown();
        musicNightScheduler.shutdown();
        DB.close();
    }

    private Database initDatabase()
    {
        if(config.getDatabaseUser().isEmpty() || config.getDatabasePassword().isEmpty() ||
                config.getDatabase().isEmpty() || config.getDatabaseHost().isEmpty())
        {
            throw new IllegalStateException("You haven't configured database details in the config file!");
        }

        DatabaseOptions options = DatabaseOptions.builder()
                .mysql(config.getDatabaseUser(), config.getDatabasePassword(), config.getDatabase(), config.getDatabaseHost())
                .driverClassName(Driver.class.getName() /*"com.mysql.cj.jdbc.Driver"*/)
                .dataSourceClassName(MysqlDataSource.class.getName() /*"com.mysql.cj.jdbc.MysqlDataSource"*/)
                .build();

        Map<String, Object> props = new HashMap<String, Object>()
        {{
            put("useSSL", config.useSSL());
            put("verifyServerCertificate", config.verifyServerCertificate());
            put("autoReconnect", config.autoReconnect());
            put("serverTimezone", "CST"); // Doesn't really matter
            put("characterEncoding", "UTF-8");
        }};

        co.aikar.idb.Database db = PooledDatabaseOptions.builder()
                .dataSourceProperties(props)
                .options(options)
                .createHikariDatabase();

        DB.setGlobalDatabase(db);

        return new Database();
    }

    private void remindMusicNight(JDA jda)
    {
        Calendar c = Calendar.getInstance();
        if(!(c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY))
        {
            // Not today, m8
            return;
        }

        TextChannel general = jda.getTextChannelById(258999527783137280L);
        if(general == null || !(general.canTalk()))
            return;

        general.sendMessage("\u23F0 <@98938149316599808> **Music night in 15 minutes!**").queue();
    }

    public BotCore getCore()
    {
        return core;
    }

    public Config getConfig()
    {
        return config;
    }

    public Database getDatabase()
    {
        return db;
    }

    public EventWaiter getWaiter()
    {
        return waiter;
    }

    public JDA getJDA()
    {
        return jda;
    }

    public ScheduledExecutorService getBotScheduler()
    {
        return botScheduler;
    }

    // Data managers
    public BirthdayDataManager getBirthdayDataManager()
    {
        return birthdayDataManager;
    }

    public CodeDataManager getCodeDataManager()
    {
        return codeDataManager;
    }

    public GuildSettingsDataManager getGuildSettingsDataManager()
    {
        return guildSettingsDataManager;
    }

    public MorpherDataManager getMorpherDataManager()
    {
        return morpherDataManager;
    }

    // Managers
    public BirthdayManager getBirthdayManager()
    {
        return birthdayManager;
    }

    public PollManager getPollManager()
    {
        return pollManager;
    }

    // Other
    public OkHttpClient getHttpClient()
    {
        return httpClient;
    }

	public String getPrefix(Guild guild)
	{
		if(guild == null)
			return config.getPrefix();
		
		return getCore().getGuildSettings(guild).getFirstPrefix();
	}
}

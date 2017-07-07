module SerieBot
  module Config
    class << self
      # The bot necessities
      attr_accessor :token
      attr_accessor :appid
      attr_accessor :prefix
      attr_accessor :playing
      attr_accessor :invite_url
      attr_accessor :bot_owners
      attr_accessor :status

      # Providers
      attr_accessor :provider
      attr_accessor :provider_options

      # Logging dump dir
      attr_accessor :dump_dir

      # Debug mode
      attr_accessor :debug

      # Server config
      attr_accessor :root_server
      attr_accessor :patch_mail
      attr_accessor :morpher_enabled
      attr_accessor :morpher_server

      # Logging options
      attr_accessor :logged_servers
      attr_accessor :ignore_ids

      # General settings (not loaded in config.rb)
      attr_accessor :settings
    end

    # Load bot configuration
    config_location = Dir.pwd + '/config.rb'
    if File.exists?(config_location)
      require_relative config_location
    else
      puts "Could not load config.rb!\n"
      puts "If this is your first time running this bot, please copy config.rb.example to config.rb and edit as necessary.\n"
      exit
    end

    # Bot settings get loaded in Helper (at the very bottom of the file!)
  end
end

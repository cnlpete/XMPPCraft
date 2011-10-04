package de.has.minecraft.xmppcraft;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.muc.ParticipantStatusListener;
import org.jivesoftware.smackx.muc.SubjectUpdatedListener;

public class XMPPCraft extends JavaPlugin {

	Logger log = Logger.getLogger("Minecraft");
	String logPrefix = "[XMPPCraft] ";
	private Configuration configuration;
	private HashMap<String, Connection> connections;
	private HashMap<String, MultiUserChat> chatrooms;

	private final class MyPacketListener implements PacketListener {
		private MultiUserChat muc;
		private Player p;

		public MyPacketListener(MultiUserChat muc, Player p) {
			super();
			
			this.muc = muc;
			this.p = p;
		}
		
		@Override
		public void processPacket(Packet packet) {

			if (packet instanceof Message) {
				Message message = (Message) packet;

				Occupant actor = muc.getOccupant(message.getFrom());
				// message not from myself ... or other player on this server
				if (!muc.getNickname().equals(actor.getNick()) && getServer().getPlayer(actor.getNick()) == null) {
					// TODO check for other members also? or disable minecraft
					// internal chat??
					String actorNick = (actor.getRole().equals("moderator") ? "@" : actor.getRole().equals("participant") ? "+" : "") + actor.getNick();
					String body = message.getBody();
					// getServer().broadcastMessage(actorNick + ": " + body);
					p.sendMessage(actorNick + ": " + body);
				}

			}
			else {
				log.warning(logPrefix + "Unknown packet intercepted of type " + packet.getClass().toString());
			}
		}

	}

	private final class MySubjectListener implements SubjectUpdatedListener {
		private Player p;

		MySubjectListener(Player p) {

			this.p = p;
		}
		@Override
		public void subjectUpdated(String subject, String from) {

			p.sendMessage("Subject changed to " + subject);
		}

	}

	private final class MyParticipantListener
			implements ParticipantStatusListener {

		private Player p;

		public MyParticipantListener(Player p) {

			this.p = p;
		}

		@Override
		public void adminGranted(String participant) {

		}

		@Override
		public void adminRevoked(String participant) {

		}

		@Override
		public void banned(String participant, String actor, String reason) {

			// if not minecraft player...
			if (getServer().getPlayer(participant) == null)
				p.sendMessage(participant + " was banned from the ChatRoom");
		}

		@Override
		public void joined(String participant) {

			// if not minecraft player...
			if (getServer().getPlayer(participant) == null)
				p.sendMessage(participant + " joined the ChatRoom");
		}

		@Override
		public void kicked(String participant, String actor, String reason) {

			// if not minecraft player...
			if (getServer().getPlayer(participant) == null)
				p.sendMessage(participant + " was kicked from the ChatRoom");
		}

		@Override
		public void left(String participant) {

			// if not minecraft player...
			if (getServer().getPlayer(participant) == null)
				p.sendMessage(participant + " left the ChatRoom");
		}

		@Override
		public void membershipGranted(String participant) {

		}

		@Override
		public void membershipRevoked(String participant) {

		}

		@Override
		public void moderatorGranted(String participant) {

		}

		@Override
		public void moderatorRevoked(String participant) {

		}

		@Override
		public void nicknameChanged(String participant, String newNickname) {

			// if not minecraft player...
			if (getServer().getPlayer(participant) == null)
				p.sendMessage(participant + " changed his Nickname to " + newNickname);
		}

		@Override
		public void ownershipGranted(String participant) {

		}

		@Override
		public void ownershipRevoked(String participant) {

		}

		@Override
		public void voiceGranted(String participant) {

		}

		@Override
		public void voiceRevoked(String participant) {

		}

	}

	private final class MyPlayerListener extends PlayerListener {

		private final XMPPCraft plugin;

		MyPlayerListener(XMPPCraft plugin) {
			this.plugin = plugin;
		}

		@Override
		public void onPlayerJoin(PlayerJoinEvent event) {

			// TODO generate Connection and save somewhere
			String host = configuration.getString("server.host", "localhost");
			int port = configuration.getInt("server.port", 5222);
			String service = host;
			ConnectionConfiguration xconfig = new ConnectionConfiguration(host, port, service);

			boolean selfsigned = configuration.getBoolean("server.selfsignedCerts", true);
			xconfig.setSelfSignedCertificateEnabled(selfsigned);

			// do we need to set this every time???
			SASLAuthentication.supportSASLMechanism("PLAIN", 0);

			log.info(logPrefix + "trying con: " + host + ":" + port);

			Connection c = new XMPPConnection(xconfig);
			try {
				c.connect();

				c.loginAnonymously();

				DiscussionHistory history = new DiscussionHistory();
				history.setMaxStanzas(0);

				MultiUserChat chatRoom = new MultiUserChat(c, configuration.getString("room.name"));
				chatRoom.addMessageListener(new MyPacketListener(chatRoom, event.getPlayer()));
				chatRoom.addParticipantStatusListener(new MyParticipantListener(event.getPlayer()));
				chatRoom.addSubjectUpdatedListener(new MySubjectListener(event.getPlayer()));
				// chatRoom.addUserStatusListener(this);
				String chosenNickname = event.getPlayer().getDisplayName();
				Iterator<String> it = chatRoom.getOccupants();
				boolean notUsedMCPrefix = true;
				Random r = new Random();
				while (it.hasNext()) {
					if (it.next().equals(chosenNickname)) {
						if(notUsedMCPrefix) {
							chosenNickname = "MC " + chosenNickname;
							notUsedMCPrefix = false;
						}
						else {
							chosenNickname = chosenNickname + r.nextInt(10);
						}
						//start from beginning...
						it = chatRoom.getOccupants();
					}
				}

				chatRoom.join(chosenNickname, configuration.getString("room.password", ""), history, SmackConfiguration.getPacketReplyTimeout());

				plugin.connections.put(event.getPlayer().getName(), c);
				plugin.chatrooms.put(event.getPlayer().getName(), chatRoom);
			} catch (XMPPException e) {
				plugin.log.severe(logPrefix + "Connection failed: " + e.getMessage());
			}

			super.onPlayerJoin(event);
		}

		@Override
		public void onPlayerChat(PlayerChatEvent event) {

			//send chat message on my connection
			String key = event.getPlayer().getName();
			if (connections.containsKey(key)) {
				Connection c = connections.get(key);
				if(c.isConnected()) {
					MultiUserChat muc = chatrooms.get(key);
					try {
						muc.sendMessage(event.getMessage());
					} catch (XMPPException e) {
						log.severe(logPrefix + "could not send message: " + e.getMessage());
					} catch (NullPointerException e) {
						log.severe(logPrefix + "MUC not found");
					}
				}
			}
			super.onPlayerChat(event);
		}

		@Override
		public void onPlayerQuit(PlayerQuitEvent event) {

			// remove this connection
			String key = event.getPlayer().getName();
			if (connections.containsKey(key)) {
				Connection c = connections.get(key);
				if (c.isConnected() && chatrooms.containsKey(key))
					chatrooms.get(key).leave();

				if (c.isConnected())
					c.disconnect();
				connections.remove(key);
			}
			super.onPlayerQuit(event);
		}
	}

	@Override
	public void onDisable() {

		// 
		Collection<MultiUserChat> mucs = chatrooms.values();
		for (MultiUserChat multiUserChat : mucs) {
			multiUserChat.leave();
		}

		// disconnect all
		Collection<Connection> convals = connections.values();
		for (Connection connection : convals) {
			if (connection.isConnected())
				connection.disconnect();
		}
	}

	@Override
	public void onEnable() {

		// read my configuration
		this.configuration = getMyConfiguration();
		SASLAuthentication.supportSASLMechanism("PLAIN", 0);
		this.connections = new HashMap<String, Connection>();
		this.chatrooms = new HashMap<String, MultiUserChat>();

		if (!configuration.getBoolean("plugin.enabled", true)) {
			log.info(logPrefix + "is disabled because of the configuration file");
			return;
		}

		PlayerListener pl = new MyPlayerListener(this);

		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_JOIN, pl, Event.Priority.Monitor,
				this);
		pm.registerEvent(Event.Type.PLAYER_CHAT, pl, Event.Priority.Monitor,
				this);
		pm.registerEvent(Event.Type.PLAYER_QUIT, pl, Event.Priority.Monitor,
				this);

		PluginDescriptionFile pdfFile = this.getDescription();
		log.info(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!");
	}

	private Configuration getMyConfiguration() {

		Configuration config = getConfiguration();
		config.load();
		if (config.getAll().size() == 0) {
			config.setProperty("server.host", "localhost");
			config.setProperty("server.port", 5222);
			// config.setProperty("server.saslAuth", false);
			config.setProperty("server.selfsignedCerts", true);
			config.setProperty("room.name", "");
			config.setProperty("room.password", "");
			config.setProperty("plugin.enabled", true);
			config.save();
		}

		return config;
	}

}

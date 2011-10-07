package de.has.minecraft.xmppcraft;

import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
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

	private HashMap<String, String> namesMap;

	private final class MyListener
			implements PacketListener, SubjectUpdatedListener,
			ParticipantStatusListener {
		private MultiUserChat muc;
		private Player p;

		public MyListener(MultiUserChat muc, Player p) {
			super();
			
			this.muc = muc;
			this.p = p;
		}
		
		private String getNickname(String nickname) {

			// try to find in chat-list
			Occupant o = muc.getOccupant(nickname);
			if (o != null)
				return o.getNick();
			else {
				String[] tmp = nickname.split("/");
				return tmp[tmp.length - 1];
			}
		}

		@Override
		public void processPacket(Packet packet) {

			if (packet instanceof Message) {
				Message message = (Message) packet;

				Occupant actor = muc.getOccupant(message.getFrom());
				// message not from myself ... or other player on this server
				
				if (isNotMinecraftUser(actor.getNick())) {
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
				p.sendMessage(ChatColor.GREEN + getNickname(participant) + ChatColor.GOLD + " was banned from the ChatRoom: " + reason);
		}

		@Override
		public void joined(String participant) {

			// if not minecraft player...
			if (getServer().getPlayer(participant) == null)
				p.sendMessage(ChatColor.GREEN + getNickname(participant) + ChatColor.GOLD + " joined the ChatRoom");
		}

		@Override
		public void kicked(String participant, String actor, String reason) {

			// if not minecraft player...
			if (getServer().getPlayer(participant) == null)
				p.sendMessage(ChatColor.GREEN + getNickname(participant) + ChatColor.GOLD + " was kicked from the ChatRoom");
		}

		@Override
		public void left(String participant) {

			// if not minecraft player...
			if (isNotMinecraftUser(participant))
				p.sendMessage(ChatColor.GREEN + getNickname(participant) + ChatColor.GOLD + " left the ChatRoom");
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
			if (isNotMinecraftUser(participant))
				p.sendMessage(ChatColor.GREEN + getNickname(participant) + ChatColor.GOLD + " changed his Nickname to " + ChatColor.GREEN + newNickname);
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

		public void subjectUpdated(String subject, String from) {

			p.sendMessage(ChatColor.GOLD + "Subject changed to " + ChatColor.GREEN + subject);
		}
	}

	private final class MyPlayerListener extends PlayerListener {

		private final XMPPCraft plugin;

		MyPlayerListener(XMPPCraft plugin) {
			this.plugin = plugin;
		}

		private void joinRoom(MultiUserChat muc, String username,
				String passwd, DiscussionHistory h, int timeout) {

			boolean njoined = true;
			Random r = new Random();

			while (njoined) {
				try {
					muc.join(username, passwd, h, timeout);
					njoined = false;
				} catch (XMPPException e) {
					// FIXME i just guess this is because of wrong username...
					if (username.substring(0, 3).equals("MC "))
						username += r.nextInt(10);
					else
						username = "MC " + username;
					njoined = true;
				}
			}

		}

		@Override
		public void onPlayerJoin(PlayerJoinEvent event) {

			// generate Connection and save somewhere
			String host = configuration.getString("server.host", "localhost");
			int port = configuration.getInt("server.port", 5222);
			String service = host;
			ConnectionConfiguration xconfig = new ConnectionConfiguration(host, port, service);

			boolean selfsigned = configuration.getBoolean("server.selfsignedCerts", true);
			xconfig.setSelfSignedCertificateEnabled(selfsigned);

			// do we need to set this every time???
			SASLAuthentication.supportSASLMechanism("PLAIN", 0);

			Connection c = new XMPPConnection(xconfig);
			try {
				c.connect();

				c.loginAnonymously();

				DiscussionHistory history = new DiscussionHistory();
				history.setMaxStanzas(0);

				MultiUserChat chatRoom = new MultiUserChat(c, configuration.getString("room.name"));
				MyListener ml = new MyListener(chatRoom, event.getPlayer());
				chatRoom.addMessageListener(ml);
				chatRoom.addParticipantStatusListener(ml);
				chatRoom.addSubjectUpdatedListener(ml);
				// chatRoom.addUserStatusListener(this);

				joinRoom(chatRoom, event.getPlayer().getDisplayName(), configuration.getString("room.password", ""), history, SmackConfiguration.getPacketReplyTimeout());

				String name = event.getPlayer().getDisplayName();
				String chatname = chatRoom.getNickname();

				plugin.connections.put(name, c);
				plugin.chatrooms.put(name, chatRoom);
				if (!name.equals(chatname)) // if we have a different name for
											// chat, add to map
					plugin.namesMap.put(name, chatname);
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
			if (namesMap.containsKey(key))
				namesMap.remove(key);
			if (connections.containsKey(key)) {
				Connection c = connections.get(key);

				if (c.isConnected() && chatrooms.containsKey(key)) {
					MultiUserChat muc = chatrooms.get(key);
					if (muc.isJoined())
						muc.leave();
					chatrooms.remove(key);
				}

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
		this.namesMap = new HashMap<String, String>();

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

	boolean isNotMinecraftUser(String name) {

		// if message from myself?
		// if (muc.getNickname().equals(name))
		// return false;

		// msg from mc-player that has his original name
		if (getServer().getPlayer(name) != null && !namesMap.containsKey(name))
			return false;

		// msg from mc player with new name
		if (getServer().getPlayer(name) == null && namesMap.containsValue(name))
			return false;

		return true;
	}

}

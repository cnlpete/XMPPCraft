XMPPCraft
=========

XMPPCraft is a connectivity plugin that allows Minecraft chat to be mirrored
in a XMPP MUC chatroom.
I know there are severel other plugins that claim to do the same, but this plugin connects every minecraft player for itself, using its nickname and some anonymous jabber server, so when visiting the MUC with some Jabber Client, you can actually see all Minecraft Players with their names instead of one "CraftXMPP"-User, that just behaves like a bot.

Build Requirements
------------------

XMPPCraft uses the [Smack API from Ignite Realtime](http://www.igniterealtime.org/projects/smack/index.jsp), a pure Java XMPP library.

Setup
-----

1. Copy craftxmpp.jar into your plugin folder
2. Load latest smack.jar and smackx.jar and copy them into your lib folder
3. Reload your server
4. Edit the configuration in plugins/CraftXMPP/
5. Reload and check that every connected User joined the ChatRoom.


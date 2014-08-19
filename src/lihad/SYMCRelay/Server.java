package lihad.SYMCRelay;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;

import org.yaml.snakeyaml.Yaml;

import lihad.SYMCRelay.Logger.Logger;


/**
 * 
 * This is the backend server for my connecting Chat Relay I'm calling SYMC Chat Relay
 * 
 * @author Kyle_Armstrong
 *
 */



public class Server {
	// initialize logger
	public static Logger log = new Logger(null);

	// This value needs to match the client, or else, refuse connection from client
	private final static int min_build = 125;

	public final static String END_CHAT_SESSION = new Character((char)0).toString(); // indicates the end of a session
	public final static String HEARTBEAT = new Character((char)1).toString(); // session Heartbeat
	public final static String CONNECTED_USERS = new Character((char)2).toString(); // this is always followed by a list of users, separated by spaces
	public final static String CHANNEL = new Character((char)3).toString(); // denotes a channel
	public final static String CHANNEL_JOIN = new Character((char)4).toString(); // sent when joining a channel
	public final static String CHANNEL_LEAVE = new Character((char)5).toString();  // sent when leaving a channel
	public final static String RETURN = new Character((char)6).toString(); // denotes a line break
	public final static String VERSION = new Character((char)7).toString(); // denotes a version
	public final static String FORMAT = new Character((char)8).toString(); // this is always followed by a format code, followed by the format request
	public final static String COUNT = new Character((char)9).toString(); //this will send a map of all open channels + user count


	// tcp components
	public static ServerSocket hostServer = null;
	public static List<User> users = new LinkedList<User>();
	public static List<User> dead_users = new LinkedList<User>();
	public static List<Channel> channels = new LinkedList<Channel>();
	public static List<ChatLine> ptp_out = new LinkedList<ChatLine>();
	public static int port = 443;


	//testing vars
	//public static String chan = "lobby,chill,ev_fl,ev_adv,ev_bl,ev_general,be,nbu,weekend,backroom";

	protected static void addUser(User u) { synchronized (users) { users.add(u);}}
	protected static void removeUser(User u) { synchronized (users) { users.remove(u);}}
	protected static List<User> getUsers() { synchronized (users) { return users;}}
	protected static User getUser(String s) { synchronized (users) { for(User u : users){if(u.name.equalsIgnoreCase(s))return u;}return null;}}
	protected static boolean containsUser(String s) { synchronized (users) { for(User u : users){if(u.name.equalsIgnoreCase(s))return true;}return false;}}

	//command stuff
	
	private static List<String> command_queue = new LinkedList<String>();

	protected static void addCommand(String command){ synchronized (command_queue) { command_queue.add(command);}}
	protected static void removeCommand(String command){ synchronized (command_queue) { command_queue.remove(command);}}
	protected static List<String> getCommandQueue(){ synchronized (command_queue) { return command_queue;}}
	protected static String popCommandFromRear(){ synchronized (command_queue) { if(command_queue.size() > 0) return command_queue.remove(0); else return null;}}

	
	// the main procedure
	public static void main(String args[]) {
		log.buff(2);
		log.noformat("starting [SYMC Relay Chat Server] - build: "+min_build);
		//attempt to start the server on port.
		try {
			hostServer = new ServerSocket(port);
			log.noformat("host created and locked on "+InetAddress.getLocalHost().getHostAddress()+":"+port+".  startup looks good.");
			log.noformat("--------------------------------------------------------------.");
		} catch (IOException e1) {
			log.error(e1.toString(), e1.getStackTrace());
			log.noformat("unable to open port. stopping.");
			System.exit(0);
		}


		//TODO: create channels
		loadChannels();

		// this thread checks for incoming connections.
		new Thread(new Runnable() {
			public void run() {
				log.info("opening connection thread");
				while(true){
					Socket sock = null;
					BufferedReader br = null;
					PrintWriter pr = null;
					try {
						sock = hostServer.accept();
						log.info("connection from ["+sock.getInetAddress().toString().replace("/", "")+"] being made to server. socket created. securing connection...");
						br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
						log.info(" ["+sock.getInetAddress().toString().replace("/", "")+"] inbound secure ");
						pr = new PrintWriter(sock.getOutputStream(), true);
						log.info(" ["+sock.getInetAddress().toString().replace("/", "")+"] outbound secure ");
						try{
							// test to see if incoming connection has a valid version.  collect username.
							// format { <version> <username> <ip> <port> }
							String[] sinfo = decode(br.readLine()).split(" ");
							log.info("connection from ["+sock.getInetAddress().toString().replace("/", "")+"] sent {version: "+sinfo[0]+"}{username: "+sinfo[1]+"}{ip: "+sinfo[2]+"}{port: "+sinfo[3]+"}");
							//version check
							if( Double.parseDouble(sinfo[0]) < min_build){
								log.warning("connection from ["+sock.getInetAddress().toString().replace("/", "")+"] has an invalid build. closing socket");
								send(pr, "you have an invalid/infant version installed. please download at least version "+min_build+" from the Relay menu.");
								close_connection(pr);
								cleanup(null,sock, br, pr);
							}else{
								//send server version
								send(pr, VERSION+min_build);
								//ip check
								if(!sock.getInetAddress().toString().replace("/", "").equalsIgnoreCase(sinfo[2])){
									log.warning("connection from ["+sock.getInetAddress().toString().replace("/", "")+"] has an mismatched ip. referencing {"+sinfo[2]+"}. closing socket");
									send(pr,"ip mismatched. stop trying to cheat, or close any proxy rerouting.");
									close_connection(pr);
									cleanup(null,sock, br, pr);
								}else{
									//user check
									boolean k = true;
									for(User u : getUsers()){
										if(u.name.equalsIgnoreCase(sinfo[1])){
											log.warning("connection from ["+sock.getInetAddress().toString().replace("/", "")+"] username already exists. referencing {"+sinfo[1]+"}. closing socket");
											send(pr, "username already exists.");
											close_connection(pr);
											cleanup(null,sock, br, pr);
											k = false;
											break;
										}
									}
									//if user passes check, build user
									if(k){
										User u = new User(sinfo[1], sock.getInetAddress().toString().replace("/", ""), sock, br, pr);
										addUser(u);
									}
								}
							}

						}catch(Exception e){
							// assuming something broke after the connection is made
							log.severe("an abnormal error has occured validating the connection. closing connection. "+sock.getInetAddress().toString().replace("/", ""));
							log.error(e.toString(), e.getStackTrace());
							if(pr != null) send(pr, "an abnormal error has occured validating the connection. contact ");
							cleanup(null,sock, br, pr);
						}
					} catch (IOException e1) {
						// assuming something broke while creating the connection
						log.severe("an abnormal error has creating the connection. closing connection.");
						log.error(e1.toString(), e1.getStackTrace());
						cleanup(null,sock, br, pr);
					}
				}
			}
		}).start();

		// this thread waits on commands.
		new Thread(new Runnable() {
			public void run() {
				@SuppressWarnings("resource")
				Scanner input = new Scanner(System.in);
				while(true){
					addCommand(input.nextLine());
				}
			}
		}).start();
		
		log.info("opening main thread");
		while (true){
			try { Thread.sleep(10); // Poll every ~10 ms 
			} catch (InterruptedException e1) {log.severe("sleeper broke");log.error(e1.toString(), e1.getStackTrace());}
			int count = 0;
			int user_spam = 0;
			while (!getUsers().isEmpty()) {
				try { Thread.sleep(10); // Poll every ~10 ms
				}catch (InterruptedException e) {log.severe("sleeper broke");log.error(e.toString(), e.getStackTrace());}
				try {
					// send any data pending
					for(ChatLine cl : ptp_out){
						if(cl.channel != null){
							for(User u : cl.channel.users){
								// if user does not equal the user who sent the message
								if(((cl.user_from == null || !cl.user_from.equals(u)) && cl.channel.users.contains(u)) 
										&& (cl.user_to == null || cl.user_to.equals(u))){
									send(u.writer, cl.channel.name+CHANNEL+cl.string);
								}
							}
						}
					}
					//send users connected every second
					if(count == 100){
						//build user list and format
						for(User u : getUsers()){
							String con = CONNECTED_USERS;
							for(Channel c : channels){
								if(c.users.contains(u)){
									con = con.concat(FORMAT+"b"+FORMAT+"#"+c.name+"_["+c.users.size()+"]");
									con = con.concat(" "+FORMAT+"!b"+FORMAT);
									for(User cu : c.users){
										con = con.concat(cu.name+" ");
									}
								}
							}
							send(u.writer, con);
						}
						//send that information to the client
						count = 0;
					}
					count ++;



					ptp_out.clear();					
					// receive any data
					for(User u : getUsers()){

						if (u.reader.ready()) {
							String s = new String(decode(u.reader.readLine()));
							StringBuffer sb = new StringBuffer();

							if (s.equals(END_CHAT_SESSION.replace("\n", ""))) {
								dead_users.add(u);
								log.info(u.name+" <"+u.ip+"> has disconnected.");
								//ptp_out.add(new ChatLine(channels.get(0), null, null, sb));
							}else if(s.equals(HEARTBEAT.replace("\n", ""))){
								u.heartbeat = 0;
							}else if(s.contains(CHANNEL_JOIN)){
								for(Channel c : channels){
									if(c.name.equalsIgnoreCase(s.replace(CHANNEL_JOIN, ""))){
										sendJoinChannel(u,c);
										if(c.dsrp != null)ptp_out.add(new ChatLine(c, null, u, new StringBuffer().append(c.dsrp)));
										c.users.add(u);
									}
								}
							}else if(s.contains(CHANNEL_LEAVE)){
								for(Channel c : channels){
									if(c.name.equalsIgnoreCase(s.replace(CHANNEL_LEAVE, ""))){
										sendLeftChannel(u,c);
										c.users.remove(u);
									}
								}
							}else if(s.contains(COUNT)){
								String s_c = "";
								for(Channel c : channels){
									s_c = s_c.concat("{"+c.name+"|"+c.users.size()+"}");
								}
								send(u.writer,COUNT+s_c);
							}else{
								sb.append(u.name+": "+FORMAT+s.split(FORMAT)[1]+FORMAT+s.split(CHANNEL)[1]);
								if ((s != null) &&  (s.length() != 0)) {
									if(isValidChannel(s.split(CHANNEL)[0].split(FORMAT)[2])){
										log.info("{"+s.split(CHANNEL)[0].split(FORMAT)[2]+"}"+u.name+": " + salt(s.split(CHANNEL)[1]));
										ptp_out.add(new ChatLine(getChannel(s.split(CHANNEL)[0].split(FORMAT)[2]), u, null, sb));
									}else{
										log.debug(u.name+" is writing to invalid channel:"+s.split(CHANNEL)[0].split(FORMAT)[2]);
										send(u.writer, (s.split(CHANNEL)[0].split(FORMAT)[2]+CHANNEL+"invalid channel"));
									}
								}
							}
						}
					}
				}
				catch (IOException e) {log.error(e.toString(), e.getStackTrace());}

				//heartbeat count
				for(User u : getUsers()){
					u.heartbeat++;
					if(u.heartbeat > 500){ 
						dead_users.add(u); 
						log.info("connection to "+u.name+" has been lost");
					}
				}
				//process dead users
				for(User u : dead_users){if(getUsers().contains(u))cleanup(u,u.socket,u.reader,u.writer);}
				dead_users.clear();

				//server reports to log how many users are connected every... minute
				if(user_spam > 6000){
					String s = "";
					for(User u : getUsers()) s.concat(u.name+", ");
					log.info("there are {"+getUsers().size()+"} users connected. "+s);
					user_spam = 0;
				}
				user_spam++;
			}
			//process any commands
			while(getCommandQueue().size() > 0){
				System.out.println("such command");
				String[] command = popCommandFromRear().toLowerCase().split(" ");
				switch(command[0]){
				case "stop": 
					log.info("[COMMAND] stopping server...");
					System.exit(0);
					break;
				case "reload":
					log.info("[COMMAND] reloading channels...");
					loadChannels();
					break;
				case "kick":
					if(command.length > 1){
						if(containsUser(command[1])){
							close_connection(getUser(command[1]));
						}else{
							log.info("[COMMAND] user "+command[1]+" does not exist.");
						}
					}else{
						log.info("[COMMAND] not enough arguments");
					}
					break;
				default: log.info("[COMMAND] invalid command");
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void loadChannels(){
		try {
			Yaml yaml = new Yaml();
			log.info("[CHANNEL] loading channels");
			File chans = new File("channels.yml"); 
			if(!chans.exists())
				chans.createNewFile();

			Map<String, Object> object = (Map<String, Object>) yaml.load(new BufferedReader(new FileReader(chans)));

			for(Entry<String, Object> o : object.entrySet()){
				String d = FORMAT+"b"+FORMAT+"[CHANNEL] ";
				List<String> l = null;
				Map<String, Object> prop = (Map<String, Object>)o.getValue();
				if(prop.containsKey("description")) d = d.concat(((String)prop.get("description")).replaceAll("\\\\n", RETURN+FORMAT+"b"+FORMAT+"[CHANNEL]"));
				if(prop.containsKey("marquee")) l = (List<String>)prop.get("marquee");
				
				boolean exists = false;
				for(Channel c : channels){
					if(c.name.equalsIgnoreCase(o.getKey())){
						log.info("[CHANNEL] updating channel: "+c.name);
						c.dsrp = d; c.marquee = l; exists = true;
					}
				}
				if(!exists){
					log.info("[CHANNEL] adding channel: "+o.getKey());
					channels.add(new Channel(o.getKey(), d, l));
				}
			}

		} catch (IOException e2) {
			log.error(e2.toString(), e2.getStackTrace());
		}
	}
	
	private static void sendLeftChannel(User leaver, Channel c){
		StringBuffer sb = new StringBuffer();
		sb.append(FORMAT+"i"+FORMAT+leaver.name+" has left the channel!");
		log.info(leaver.name+" has left channel '"+c.name+"'");
		ptp_out.add(new ChatLine(c, null, null, sb));
	}
	private static void sendJoinChannel(User joiner, Channel c){
		StringBuffer sb = new StringBuffer();
		sb.append(FORMAT+"i"+FORMAT+joiner.name+" has joined the channel!");
		log.info(joiner.name+" has joined channel '"+c.name+"'");
		ptp_out.add(new ChatLine(c, null, null, sb));
	}

	private static String salt(String string){
		String base = "";
		byte[] ba = string.getBytes();
		for(byte b : ba)base = base.concat(b+"");	
		return scramble(base);
	}

	private static String scramble(String string){
		for(int i = 0; i < string.length()/2; i++){
			if(i % 2 == 0){
				string = string.concat(String.valueOf(string.charAt(i)));
				string = string.substring(0,i)+string.substring(i+1);
			}
		}
		return string;
	}

	private static void send(PrintWriter pr, String s){
		pr.print(encode(s)+"\n"); pr.flush();
	}
	private static String encode(String string){
		byte[] b_a = string.getBytes();
		String s = "";
		for(byte b : b_a)s = s.concat(b+".");
		s = s.substring(0, s.length()-1);
		return s;
	}
	private static String decode(String s){
		try {
			String[] s_a = s.split("\\.");
			byte[] b = new byte[s_a.length];
			for(int k = 0; k<s_a.length;k++){b[k] = Byte.parseByte(s_a[k]);}
			return new String(b, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.error(e.toString(), e.getStackTrace());
			return "error";
		}
	}

	// helper methods
	protected static void createChannel(String name, String description, List<String> marquee){channels.add(new Channel(name, description, marquee));}

	protected static void close_connection(PrintWriter pr){send(pr, END_CHAT_SESSION);}

	protected static void close_connection(User u){send(u.writer, END_CHAT_SESSION); cleanup(u, u.socket, u.reader, u.writer);}

	protected static Channel getChannel(String name){for(Channel c : channels) if(c.name.equalsIgnoreCase(name))return c; return null;}

	protected static void dumpChannel(User u, Channel c){c.users.remove(u);}

	protected static void dumpChannelAll(User u){for(Channel c : channels)if(c.users.contains(u)){sendLeftChannel(u,c); c.users.remove(u);}}

	protected static boolean isValidChannel(String name){for(Channel c : channels) if(c.name.equalsIgnoreCase(name))return true; return false;}

	// Cleanup for disconnect
	private static void cleanup(User u, Socket s, BufferedReader b, PrintWriter p){
		try { if(u != null){dumpChannelAll(u);}if(p != null){send(p, END_CHAT_SESSION); p.close();}; if(b != null)b.close(); if(s != null)s.close(); removeUser(u);} catch (IOException e) {log.error(e.toString(), e.getStackTrace());}}

}


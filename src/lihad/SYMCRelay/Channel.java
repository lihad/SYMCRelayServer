package lihad.SYMCRelay;

import java.util.LinkedList;
import java.util.List;

public class Channel {

	String name;
	String dsrp;
	List<String> marquee;
	List<User> users;
	
	Channel(String n, String d, List<String> m){name=n;dsrp=d;marquee=m;users = new LinkedList<User>();}
}

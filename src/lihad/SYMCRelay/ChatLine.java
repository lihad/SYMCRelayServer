package lihad.SYMCRelay;

public class ChatLine {

	Channel channel;
	User user_from;
	User user_to;  
	StringBuffer string;
	
	ChatLine(Channel c, User u_from, User u_to, StringBuffer s){channel=c;user_from=u_from;user_to=u_to;string=s;}
}

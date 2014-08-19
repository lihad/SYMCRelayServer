package lihad.SYMCRelay;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

public class User {

	String name;
	String ip;
	Socket socket;
	BufferedReader reader;
	PrintWriter writer;
	int heartbeat = 0;
	
	User(String n, String i, Socket s, BufferedReader br, PrintWriter pr){name=n;ip=i;socket=s;reader=br;writer=pr;}
}

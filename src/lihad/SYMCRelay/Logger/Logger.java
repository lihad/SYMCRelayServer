package lihad.SYMCRelay.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Logger {
	BufferedWriter writer;
	SimpleDateFormat dateformat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	public Logger(File file){loadLog(file);}
	public void loadLog(File file){
		if(file == null){
			file = new File("symcrelayserver.txt");
		}
		try {
			writer = new BufferedWriter(new FileWriter(file, true));
		} catch (IOException e) {
			System.out.println("failed to start logger.  invalid file: "+file.getPath());
			e.printStackTrace();
		}
	}

	public void buff(int x){
		try {for(int i = 0;i<x;i++){writer.newLine();} writer.flush();} catch (IOException e){e.printStackTrace();}
	}
	public void noformat(String string){
		try { writer.write(string); writer.newLine(); writer.flush(); System.out.println(string);} catch (IOException e){e.printStackTrace();}
	}
	public void info(String string){
		try { String s = (dateformat.format(Calendar.getInstance().getTime())+" [info] "+string); writer.write(s); writer.newLine();  writer.flush();
		System.out.println(s);} catch (IOException e){e.printStackTrace();}
	}
	public void warning(String string){
		try { String s = (dateformat.format(Calendar.getInstance().getTime())+" [warning] "+string); writer.write(s); writer.newLine();  writer.flush();
		System.out.println(s);} catch (IOException e){e.printStackTrace();}
	}
	public void severe(String string){
		try { String s = (dateformat.format(Calendar.getInstance().getTime())+" [severe] "+string); writer.write(s); writer.newLine();  writer.flush();
		System.out.println(s);} catch (IOException e){e.printStackTrace();}
	}
	public void error(String s, StackTraceElement[] t){
		severe(s);
		for(StackTraceElement t_a : t)
			try {writer.write(t_a.toString()); writer.newLine();  writer.flush(); System.out.println(t_a.toString());} catch (IOException e){e.printStackTrace();}
	}
	public void debug(String string){
		try { String s = (dateformat.format(Calendar.getInstance().getTime())+" [debug] "+string); writer.write(s); writer.newLine();  writer.flush();
		System.out.println(s);} catch (IOException e){e.printStackTrace();}
	}
}

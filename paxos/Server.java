package paxos;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.Object;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

import paxos.ServerThread;
import paxos.Message;

public class Server
{
    public static void main(String[] args)
    {
        int port = 4567;

        if (args.length == 1) 
        {
            System.out.println("Using input port");
            port = Integer.parseInt(args[0]);
        }

        Map<Integer, ServerThread> mThreads = new HashMap<Integer, ServerThread>();
        ConcurrentHashMap<Integer, Message> mMessages = new ConcurrentHashMap<>();

        mMessages.put(0, new Message(null));

        try 
        {
            Selector selector = Selector.open(); 

            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open(); 
            ServerSocket serverSocket = serverSocketChannel.socket(); 
            serverSocket.setSoTimeout(30000);
            serverSocket.bind( new InetSocketAddress("localhost", port)); 
            serverSocketChannel.configureBlocking(false); 
            int ops = serverSocketChannel.validOps(); 
            serverSocketChannel.register(selector, ops, null); 
            System.out.println("Server is listening on port " + port);

            while (true) 
            { 
                //Selector checks if client has sent any keys
                selector.select(); 
                Set<SelectionKey> selectedKeys = selector.selectedKeys(); 
                Iterator<SelectionKey> i = selectedKeys.iterator(); 
  
                while (i.hasNext()) 
                { 
                    SelectionKey key = i.next(); 
                    
                    //If client wants to join, it will send a key that is acceptable
                    if (key.isAcceptable()) 
                    { 
                        // New client has been  accepted 
                        Thread thread = new Thread(new ServerThread(serverSocketChannel, mMessages));
                        thread.start();
                    } 

                    i.remove(); 
                } 
            }
 
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}

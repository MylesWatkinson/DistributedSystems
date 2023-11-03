package test;

import paxos.Server;

public class ThreadedServer extends Thread {
    
    private int port;

    public ThreadedServer(int p)
    {   
        port = p;
    }

    public void run()
    {
        String[] args = {Integer.toString(port)};

        Server server = new Server();

        server.main(args);
    }   
}

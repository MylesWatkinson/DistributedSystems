package server;

import parsers.FileParser;
import parsers.HTTPParser;
import parsers.HTTPObject;
import util.LamportClock;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerThread extends Thread
{
    private Socket mSocket;
    private FileParser mFileParser = new FileParser();
    private static String HTTPParserInput = "./parsers/HTTPParser.txt";
    private HTTPParser mHTTPParser = new HTTPParser(HTTPParserInput);
    private LamportClock mLamportClock;
    public Boolean exit = false;

    public ServerThread(Socket client, LamportClock lamportClock) throws FileNotFoundException, IOException
    {
        mSocket = client;
        mLamportClock = lamportClock;
    }

    //Start a server thread that handles one socket, 
    public void run()
    {
        try 
        {
            String clientInput = "";

            BufferedReader reader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(mSocket.getOutputStream(), true);

            String line = "";
            long lastMessage = System.currentTimeMillis();
            long allowedTime = 30000;

            while (line != null && lastMessage + allowedTime > System.currentTimeMillis() && !exit)
            {
                try
                {   
                    HTTPObject http = mHTTPParser.parse(reader);

                    if (http.type != HTTPObject.RequestType.NULL)
                    {
                        System.out.println("New request: " + http.type);
                        lastMessage = System.currentTimeMillis();

                        mLamportClock.addTime(http.lamportTime);
                        mLamportClock.newTime(http.lamportTime);

                        //We don't continue until our current job is the most recent
                        Boolean cont = false;

                        while (!cont)
                        {
                            try
                            {   
                                Thread.sleep(50);

                                cont = mLamportClock.checkForContinue(http.lamportTime);
                            }
                            catch (InterruptedException e)
                            {
                                System.out.println("Thread interrupted while waiting");
                                break;
                            }
                        }

                        if (http.responseCode > 201)
                        {
                            writer.println("HTTP/1.1 " + http.code + " " + http.errorMessage);
                            writer.println("User-Agent: ATOMClient/1/0");
                            writer.println("Content-Type: weather.json");
                            writer.println("Lamport-Time: " + Integer.toString(mLamportClock.increment()));
                            writer.println("Content-Length:0");
                        }
                        else if (http.type == HTTPObject.RequestType.PUT)
                        {
                            long currentTime = System.currentTimeMillis();
                            Boolean created = mFileParser.PlaceInFile(http.data.get(0), currentTime, http.lamportTime);

                            System.out.println("Added content server data to file");

                            if (created)
                            {
                                writer.println("HTTP/1.1 201 Created");
                            }
                            else
                            {
                                writer.println("HTTP/1.1 200 OK");
                            }

                            writer.println("User-Agent: ATOMClient/1/0");
                            writer.println("Content-Type: text/plain");
                            writer.println("Lamport-Time: " + Integer.toString(mLamportClock.increment()));
                            writer.println("Content-Length:0");
                        }
                        else if (http.type == HTTPObject.RequestType.GET)
                        {
                            handleGET(writer, http);
                        }

                        mLamportClock.checkForFinish(http.lamportTime);
                    }

                }
                catch (Exception e)
                {
                    writer.println("HTTP/1.1 500 Internal server error");
                    writer.println("User-Agent: ATOMClient/1/0");
                    writer.println("Content-Type: text/plain");
                    writer.println("Lamport-Time: " + Integer.toString(mLamportClock.increment()));
                    writer.println("Content-Length:0");
                }
            }

            reader.close();
            writer.close();
        } 
        catch (IOException e)
        {
            System.out.println("IOException: " + e);
        }

    }

    private void handleGET(PrintWriter writer, HTTPObject http)
    {
        ArrayList<String> allData = new ArrayList<String>();
        int len = 0;

        if (http.pathName.equals("weather.json"))
        {
            try
            {
                allData = mFileParser.ReturnFromFile();
                sendResponse(writer, 200, "OK", mLamportClock.increment(), allData);
            }
            catch (Exception e)
            {
                System.out.println("Exception when reading file: " + e);
                if (e.toString().contains("503"))
                {
                    sendResponse(writer, 503, "Service unavailble", mLamportClock.increment(), allData);
                }
                else
                {
                    sendResponse(writer, 500, "Internal server error", mLamportClock.increment(), allData);
                }
            }
        }
        else if (http.pathName.equals("lamport"))
        {
            sendResponse(writer, 200, "OK", mLamportClock.increment(), allData);
        }
        else
        {
            sendResponse(writer, 400, "Bad request",  mLamportClock.increment(), allData);
        }
    }

    private void sendResponse(PrintWriter writer, int code, String message, int time, ArrayList<String> data)
    {
        writer.println("HTTP/1.1 " + code + " " + message);
        writer.println("User-Agent: ATOMClient/1/0");
        writer.println("Content-Type: none");
        writer.println("Lamport-Time: " + time);
        
        int len = 0;

        if (data.size() > 0)
        {
            for (String s : data) len += s.length();
        }

        if (len == 0)
        {
            writer.println("Content-Length:0");
        }
        else
        {
            writer.println("Content-Length:" + len);
            for (String s : data) writer.println(s);
        }
    }
}
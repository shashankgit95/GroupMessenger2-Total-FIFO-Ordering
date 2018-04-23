package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Queue;
import java.util.Random;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import static java.lang.Boolean.TRUE;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final int SERVER_PORT = 10000;
    static final String[] ports = {REMOTE_PORT0,REMOTE_PORT1,REMOTE_PORT2,REMOTE_PORT3,REMOTE_PORT4};
    static String myPort = "";
    static int failedProcess = -1;

    private Uri mUri;
    private ContentResolver mContentResolver;
    private ContentValues mContentValue;
    private int proposedSeqno = 0;
    private int agreedSeqno = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            serverSocket.setReuseAddress(true);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch (IOException e)
        {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));


        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button Sendbutton = (Button)findViewById(R.id.button4);
        Sendbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Log.v(TAG, "buttonclk");
                String msg = editText.getText().toString() + "\n";
                editText.setText("");

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            //try {

                float agreedseqno = 0;
                String proposal = "";
                float proposedseqno = 0;

                Data data = new Data();
                data.message = msgs[0];
                data.type = 1;

                Random rand = new Random();
                data.msgid = rand.nextInt();

                Log.v("Msg at client:",data.message );
                Log.v("ID:",Integer.toString(data.msgid));

                String datatosend = Integer.toString(data.type);
                datatosend += "/gmessenger2/";
                datatosend += data.message;
                datatosend += "/gmessenger2/";
                datatosend += Float.toString(data.seqno);
                datatosend += "/gmessenger2/";
                datatosend += Integer.toString(data.msgid);
                datatosend += "/gmessenger2/";
                datatosend += myPort;

                for(int i =0; i<5 ; i++)
                {
                    if(i!=failedProcess)
                    agreedseqno = sendMessage(Integer.parseInt(ports[i]),datatosend,agreedseqno);
                }

                String agreemsg = "2/gmessenger2/"+Integer.toString(data.msgid)+"/gmessenger2/"+Float.toString(agreedseqno)+"/gmessenger2/"+myPort;

                Log.v("sending Agree for Msg:",data.message);
                Log.v("ID:", Integer.toString(data.msgid));
                Log.v("Agreeseqno:", Float.toString(agreedseqno));
                for(int i=0; i<5; i++)
                {
                    if(i!=failedProcess)
                    sendAgree_Failure(Integer.parseInt(ports[i]),agreemsg);
                    Log.v("agree sent to",ports[i]);

                }

            return null;
        }

        float sendMessage(int port, String message, float agreedseqno)
        {
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[] {10,0,2,2}), port);
                socket.setSoTimeout(5000);

                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF(message);
                DataInputStream input = new DataInputStream(socket.getInputStream());

                String proposal = input.readUTF();
                float proposedseqno = Float.parseFloat(proposal);
                agreedseqno = Math.max(proposedseqno,agreedseqno);

            } catch (IOException e) {
                e.printStackTrace();

                failedProcess = (port-11108)/4;
                Log.v("process failed ID:",Integer.toString(failedProcess));
                String failuremsg = "3/gmessenger2/"+Integer.toString(failedProcess);
                for(int i=0; i<5; i++)
                {
                    if(i!=failedProcess)
                    sendAgree_Failure(Integer.parseInt(ports[i]),failuremsg);
                }
            }

            return agreedseqno;
        }

        void sendAgree_Failure(int port, String message)
        {
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[] {10,0,2,2}), port);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeUTF(message);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Boolean> {

        @Override
        protected Boolean doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String Message;

            int del_sequence_number = 0;
            int proposalCount = 0;
            int maxProposal = 0;
            Comparator<Data> comparator = new PriorityComparator();
            PriorityQueue<Data> pQueue = new PriorityQueue<Data>(20, comparator);
            Queue<Data> queue = new LinkedList<Data>();

            while (true) {
                try {
                    Socket newsocket = serverSocket.accept();
                    DataInputStream input = new DataInputStream(newsocket.getInputStream());
                    String dataRead = input.readUTF();
                    Data data = new Data();

                    Scanner s = new Scanner(dataRead).useDelimiter("/gmessenger2/");
                    data.type = s.nextInt();

                   // int client_Port;

                    if(data.type == 1)
                    {
                        data.message = s.next();
                        data.seqno = s.nextFloat();
                        data.msgid = s.nextInt();
                        data.port = s.nextInt();

                        //Data proposal = new Data();
                        data.seqno = (agreedSeqno>proposedSeqno) ? (agreedSeqno) : (proposedSeqno);
                        data.seqno = data.seqno + 1 + (float)((Integer.parseInt(myPort)-11108)/4+1)/10;
                        proposedSeqno = (int)data.seqno;
                        String datatosend = Float.toString(data.seqno);

                        //Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),client_Port);
                        DataOutputStream dataOutputStream = new DataOutputStream(newsocket.getOutputStream());
                        dataOutputStream.writeUTF(datatosend);

                        pQueue.add(data);
                        Log.v("Received Msg",data.message+","+data.msgid);
                        Log.v("From port", Integer.toString(data.port));
                        Log.v("Sending Proposal",Float.toString(data.seqno));

                    }

                    if(data.type == 2)
                    {
                        data.msgid = s.nextInt();
                        data.seqno = s.nextFloat();

                        if(agreedSeqno<data.seqno)
                        agreedSeqno = (int) data.seqno;

                        data.port = s.nextInt();

                        Log.v("T.agree from process",Integer.toString(data.port));
                        Log.v("T.for Msg",Integer.toString(data.msgid));
                        Log.v("T.agreeseqno",Float.toString(data.seqno));

                        Log.v("Printing pQueue",myPort);
                        PriorityQueue<Data> pCopy = new PriorityQueue<Data>(20,comparator);
                        pCopy.addAll(pQueue);
                        while(pCopy.peek()!= null)
                        {
                            Data pdata = pCopy.poll();
                            Log.v("pData",pdata.msgid+","+pdata.message+","+Integer.toString(pdata.port)+","+Integer.toString(pdata.type)+","+Float.toString(pdata.seqno));
                        }

                        while(pQueue.peek()!=null)
                        {
                            Data head = new Data();
                            head = pQueue.poll();

                            if(head.msgid == data.msgid)
                            {
                                data.message = head.message;
                                pQueue.add(data);
                                break;
                            }
                            else
                            {
                                queue.add(head);
                            }
                        }

                        while (queue.peek()!=null)
                        {
                            pQueue.add(queue.poll());
                        }
                        pCopy.addAll(pQueue);

                        Log.v("Printing Updated pQueue",myPort);
                        while(pCopy.peek()!= null)
                        {
                            Data pdata = pCopy.poll();
                            Log.v("pData",pdata.msgid+","+pdata.message+","+Integer.toString(pdata.port)+","+Integer.toString(pdata.type)+","+Float.toString(pdata.seqno));
                        }

                        while (pQueue.peek() != null)
                        {
                            if (pQueue.peek().type == 2)
                            {
                                Data deliverdata = new Data();
                                deliverdata = pQueue.remove();
                                Deliver(deliverdata, del_sequence_number);
                                del_sequence_number++;
                            }
                            else
                                break;

                        }

                    }
                    if(data.type == 3)
                    {
                        failedProcess = s.nextInt();
                        int failedport = failedProcess*4 + 11108;

                        Log.v("failed process port:",Integer.toString(failedport));
                        Log.v("printing pQueue:",myPort);

                        while(pQueue.peek()!= null)
                        {
                            Data hdata = pQueue.poll();
                            Log.v("Data",hdata.msgid+","+hdata.message+","+Integer.toString(hdata.port)+","+Integer.toString(hdata.type)+","+Float.toString(hdata.seqno));
                            if(hdata.port == failedport)
                            {

                            }
                            else
                            {
                                queue.add(hdata);
                            }
                        }

                        while (queue.peek()!=null)
                        {
                            pQueue.add(queue.poll());
                        }

                        PriorityQueue<Data> pCopy = new PriorityQueue<Data>(20,comparator);
                        pCopy.addAll(pQueue);

                        Log.v("Printing Updated pQueue",myPort);
                        while(pCopy.peek()!= null)
                        {
                            Data pdata = pCopy.poll();
                            Log.v("pData",pdata.msgid+","+pdata.message+","+Integer.toString(pdata.port)+","+Integer.toString(pdata.type)+","+Float.toString(pdata.seqno));
                        }

                        while (pQueue.peek() != null)
                        {
                            if (pQueue.peek().type == 2)
                            {
                                Data deliverdata = new Data();
                                deliverdata = pQueue.remove();
                                Deliver(deliverdata, del_sequence_number);
                                del_sequence_number++;
                            }
                            else
                                break;
                        }
                    }

                    input.close();
                    newsocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private  void Deliver(Data d, int seqno)
        {
                mContentResolver = getContentResolver();
                mUri = buildUri("content","edu.buffalo.cse.cse486586.groupmessenger2.provider");
                mContentValue = new ContentValues();
                mContentValue.put("key",Integer.toString(seqno));
                mContentValue.put("value",d.message.trim());

                mContentResolver.insert(mUri,mContentValue);

                Log.v("T.Msg Delivered",d.message);
                Log.v("T.seqno",Integer.toString(seqno));

        }
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private class Data
    {
        String message = "";
        int type = 0;
        float seqno = 0;
        int msgid = 0;
        int port = 0;

        Data(Data d)
        {
            this.message = d.message;
            this.seqno = d.seqno;
            this.msgid = d.msgid;
            this.type = d.type;

        }
        Data ()
        { }

    }

    public class PriorityComparator implements Comparator<Data>
    {
        @Override
        public  int compare(Data a, Data b)
        {
            if(a.seqno > b.seqno)
            {
                return 1;
            }
            else if(a.seqno < b.seqno)
            {
                return -1;
            }
            else
            return 0;
        }
    }
}

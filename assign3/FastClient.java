


/**
 * FastClient Class
 *
 * FastClient implements a basic reliable FTP client application based on UDP data transmission and selective repeat protocol
 * Marcus Tang 10086730
 * For some reason, it doesn't work when the very first packet is corrupt, else it passes everything else.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class FastClient {

 	/**
        * Constructor to initialize the program
        *
        * @param server_name    server name or IP
        * @param server_port    server port
        * @param window         window size
	* @param timeout	time out value
        */

        String server; //server name
        int to; // timeout period
        int port; // port number
        int seqNum; // sequence number
        int winNum;
        Socket socket; // socket connection to connect to server
		TxQueue queue;// initalize queue
		InetAddress IPAddress;// initialize Ip address
		

	public FastClient(String server_name, int server_port, int window, int timeout) {

	/* initialize */
     this.server = server_name;
     this.port = server_port;
     this.to = timeout;
     this.winNum = window;
	
	}

	/* send file
        * @param file_name      file to be transfered
	*/

	public void send(String file_name) {
    try{
  		    //connects to port server listening at port given in argument
  		    socket = new Socket (server, port);

  		    //FileOutputStream Fout = new FileOutputStream(file);
  		    // create data output stream
  		    DataOutputStream Dout = new DataOutputStream(socket.getOutputStream());

  		    //Client sends file name to the server
  		    Dout.writeUTF(file_name);


          // Creates data input stream and reads a byte from the file
          DataInputStream Dins = new DataInputStream(socket.getInputStream());
          byte response = Dins.readByte();
          
          // Checks to see if the server is ready to read bytes from client
  	      if(response == 0){
               // creates with the string name given as an argument
  			   File transfer = new File(file_name);
               /* Creates a segment package for each byte to prepared for server
               to receive*/
              
               // Creates an array with length of file to store bytes from file
  			    byte[] chunks = new byte[(int) transfer.length()];
               // Creates UDP connection
  			    DatagramSocket clientSocket = new DatagramSocket(5555);
  			    IPAddress = InetAddress.getByName("localhost");
  		   	    FileInputStream Fin = new FileInputStream(transfer);
               /* Gets the length of the file to check if a thousand bytes can be
               taken out of the array. */
  		  	    int length = Fin.read(chunks, 0, chunks.length);
  		  	    Fin.close();
				int startOff = 0;
				int endOff = 1000;
				queue = new TxQueue(winNum);
                ACKThread AckRcv = new ACKThread(clientSocket);
			    AckRcv.start();
               /* Taking out a thousand bytes from the array until there is less
               than a thousand bytes left in the file to read */
               while(length > 1000){
				  Segment seg = new Segment ();
				  byte[] maxPay = Arrays.copyOfRange(chunks, startOff, endOff);
                  seg.setPayload(maxPay);
                  seg.setSeqNum(seqNum);
				  System.out.println(seqNum);
				  
				  //Send the packet to the server
				  try{
					  processSend(seg,  clientSocket);
					  seqNum = seqNum + 1;
				  }catch(Exception e){
					  System.out.println("Error in adding thread to queue.");
				  }
				  //the offset
                  startOff = startOff + seg.MAX_PAYLOAD_SIZE;
                  endOff = endOff + seg.MAX_PAYLOAD_SIZE;
                  length = length - 1000;
				   
				  
  			      }
				  
				 // The last part of the file being sent
                 Segment seg = new Segment ();
                 byte[] smallArry = Arrays.copyOfRange(chunks, 0, length);
                 seg.setPayload(smallArry);
                 seg.setSeqNum(seqNum);
				 try{
					 processSend(seg, clientSocket);  
				  }catch(Exception e){
					  System.out.println("Error in adding last thread to queue.");
				  }
               
				//Need to check if queue is empty before ending
				
				while(true){
					if(queue.getHeadNode() != null){
						System.out.println(queue.getHeadNode());
						continue;
					}
					else{
						AckRcv.setFlag(0);
						break;
					}
				}


           }else{
             System.out.println("Server is not ready");
			 Dout.close();
             Dins.close();
             socket.close();
           }
           // Closing off all streams
		   
           Dout.writeByte(0);
		   Dout.flush();
           Dout.close();
           Dins.close();
           socket.close();


  	}catch (IOException e){
  	   System.out.println(e);
  	}

  }
  // Method that sends packets 
  public synchronized void processSend (Segment pack, DatagramSocket socket) {

  try{
       DatagramPacket packet = new DatagramPacket(pack.getBytes(), pack.getLength(), IPAddress, port);
       try{
		  queue.add(pack);
		  queue.getNode(seqNum).setStatus(queue.getNode(seqNum).SENT);
		  socket.send(packet);
		  try{ 
			socket.setSoTimeout(to);
		  }catch(SocketException e){
			System.out.println(e);
	      }
			
		  
	    }catch(Exception e){
		   System.out.println("pp");
	    }
      
    }catch(Exception e){
       System.out.println("error");
       //seqNum = (seqNum - 1) % 2; //might not need to reset seqNum
       //packetSent(pack, address, portNum, sock, payLength);
    }
 }

	
	// Thread Class for ACK. Looks for recieving ACKS and will resend the packet if timer expires
	  class ACKThread extends Thread {
         int ackNum;
		 int flag = 1;
		 DatagramSocket dsock;
		 //constructor for the thread
		 public ACKThread(DatagramSocket ds){
			 this.dsock = ds;
		 }
		 //Will stop the thread when the program is over
		 public void setFlag(int set){
			 this.flag = 0;
		 }
		 // main thread method for looking for acks and checking timer
		 public void run(){
			 while(flag == 1){
				try{
			    
				byte [] recArry = new byte[1000];
				DatagramPacket receivePacket = new DatagramPacket(recArry, 1000);
				Segment ackseg;
					        try{
								dsock.receive(receivePacket);	 //check for expired packet
								ackseg = new Segment(recArry);   //acknowledges packets if received
								ackNum = ackseg.getSeqNum();
								queue.getNode(ackNum).setStatus(queue.getNode(ackNum).ACKNOWLEDGED);
							}catch(Exception e){
								ackseg = queue.getHeadSegment(); // when the timer expires
								int check = ackseg.getSeqNum(); 
								DatagramPacket packet = new DatagramPacket(ackseg.getBytes(), ackseg.getLength(), IPAddress, port);
								dsock.send(packet);
								continue;
								
							}
							// removes acknowledged packets from the queue if at the front of queue
							while(queue.getHeadNode() != null && (queue.getHeadNode().getStatus() == queue.getHeadNode().ACKNOWLEDGED)){
								try{
									queue.remove();
								}catch(Exception e){
									System.out.println(e);
								}
							}	
				
				}catch(Exception e){
					continue;
						
				}	
			}
		 
	    }

      }
	

  



    /**
     * A simple test driver
     *
     */
	public static void main(String[] args) {
		int window = 10; //segments
		int timeout = 100; // milli-seconds (don't change this value)

		String server = "localhost";
		String file_name = "";
		int server_port = 0;

		// check for command line arguments
		if (args.length == 4) {
			// either provide 3 parameters
			server = args[0];
			server_port = Integer.parseInt(args[1]);
			file_name = args[2];
			window = Integer.parseInt(args[3]);
		}
		else {
			System.out.println("wrong number of arguments, try again.");
			System.out.println("usage: java FastClient server port file windowsize");
			System.exit(0);
		}


		FastClient fc = new FastClient(server, server_port, window, timeout);

		System.out.printf("sending file \'%s\' to server...\n", file_name);
		fc.send(file_name);
		System.out.println("file transfer completed.");
	}

}

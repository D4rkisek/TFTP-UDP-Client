package client;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPUDPClient {
    private static final int TFTP_PORT = 9222;// TFTP port
    private static final String DEFAULT_ADDRESS = "127.0.0.1"; // Default server address (localhost)
    private static final int TIMEOUT = 5000; // Socket timeout

    public static void main(String[] args) throws IOException {
        String address = DEFAULT_ADDRESS;
        int portNumber = TFTP_PORT;

        if (args.length != 2) {
            System.out.println("Using default address: " + DEFAULT_ADDRESS + " and default port: " + TFTP_PORT);
        } else {
            address = args[0];
            portNumber = Integer.parseInt(args[1]);
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT); // Time out if socket is not sending
            InetAddress serverAddress = InetAddress.getByName(address); // Create an address

            // Read the user inputs to choose which operation to execute
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Enter 1 to send a file or 2 to retrieve a file:");

            String userInput = stdIn.readLine();
            if ("1".equals(userInput)) { //WRQ
                System.out.println("Enter the file name to send:");
                String fileName = stdIn.readLine();
                sendFile(socket, serverAddress, portNumber, fileName);
            } else if ("2".equals(userInput)) { //RRQ
                System.out.println("Enter the file name to retrieve:");
                String fileName = stdIn.readLine();
                TFTPUtil.retrieveFile(socket, serverAddress, portNumber, fileName);
            } else {
                System.out.println("Invalid option. Exiting...");
            }
        }
    }

    private static void sendFile(DatagramSocket socket, InetAddress serverAddress, int portNumber, String fileName) throws FileNotFoundException {
        // If the specified file does not exist then the code below will output a "File not found" error
        File file = new File(fileName);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + fileName);
        }
        try {
            DatagramPacket sendPacket = TFTPUtil.createWriteRequestPacket(serverAddress, portNumber, fileName);
            socket.send(sendPacket); // Sends packets to specific serverAddress and portNumber
            System.out.println("Packet 0 sent to " + serverAddress + ":" + portNumber); // Sender sends pkt0

            // Wait for the initial ACK packet with block number 0 from the server
            byte[] buffer = new byte[4];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);
            System.out.println("Received packet from " + receivePacket.getAddress() + ":" + receivePacket.getPort());

            // Check if the received packet is an ACK packet with block number 0
            short receivedOpcode = TFTPUtil.getOpcode(receivePacket);
            short receivedBlockNumber = ByteBuffer.wrap(receivePacket.getData()).getShort(2);
            if (receivedOpcode == TFTPUtil.OP_ACK && receivedBlockNumber == 0) {
                TFTPUtil.sendFile(socket, serverAddress, portNumber, fileName);
            }// else {
             //    System.out.println("Error: expected initial ACK packet with block number 0");
             // }
        } catch (IOException e) {
            System.out.println("Error sending file: " + e.getMessage());
        }
    }
}

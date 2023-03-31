package client;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPUtil {
    // Constants for TFTP opcodes
    public static final short OP_RRQ = 1; // Sending a file
    public static final short OP_WRQ = 2; // Retrieving a file
    public static final short OP_DATA = 3; // Data packet
    public static final short OP_ACK = 4; // Acknowledgements

    public static DatagramPacket createWriteRequestPacket(InetAddress serverAddress, int portNumber, String fileName) {
        byte[] fileNameBytes = fileName.getBytes();
        byte[] modeBytes = "octet".getBytes();
        byte[] requestPacket = new byte[2 + fileNameBytes.length + 1 + modeBytes.length + 1];

        // Set the opcode to WRQ (2)
        requestPacket[0] = 0;
        requestPacket[1] = 2;

        // Set the filename and mode
        System.arraycopy(fileNameBytes, 0, requestPacket, 2, fileNameBytes.length);
        requestPacket[2 + fileNameBytes.length] = 0;
        System.arraycopy(modeBytes, 0, requestPacket, 2 + fileNameBytes.length + 1, modeBytes.length);
        requestPacket[2 + fileNameBytes.length + 1 + modeBytes.length] = 0;

        return new DatagramPacket(requestPacket, requestPacket.length, serverAddress, portNumber);
    }

    // Method for creating DATA packets
    public static DatagramPacket createDataPacket(InetAddress serverAddress, int port, short blockNumber, byte[] data, int dataLength) {
        // Creates an instance of ByteArrayOutputStream and DataOutputStream to write the data to the byte array
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        try { // OP_DATA = 2 bytes, blockNumber = 2 bytes
            dataOutputStream.writeShort(OP_DATA);
            dataOutputStream.writeShort(blockNumber);
            dataOutputStream.write(data, 0, dataLength);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] dataPacketData = byteArrayOutputStream.toByteArray();
        return new DatagramPacket(dataPacketData, dataPacketData.length, serverAddress, port);
        // It returns a DatagramPacket with the byte array, its length, server address, and port number
    }

    // Method for creating ACK packets
    public static DatagramPacket createAckPacket(InetAddress serverAddress, int port, short blockNumber) {
        byte[] ackData = new byte[4]; // 4 Bytes size
        ByteBuffer.wrap(ackData).putShort((short) 4).putShort(blockNumber);
        return new DatagramPacket(ackData, ackData.length, serverAddress, port);
        // It returns a ackData with the byte array, its length, server address, and port number
    }

    public static void sendFile(DatagramSocket socket, InetAddress serverAddress, int port, String fileName) throws IOException {
        File file = new File(fileName);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            // Initialisation
            short blockNumber = 0; // block number to 0
            byte[] buffer = new byte[516];
            int bytesRead;

            // Reads the file contents into the buffer and continue until the end of the file is reached
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                // Creates a new data packet with the server address, port, block number, buffer, and the number of bytes read
                DatagramPacket dataPacket = createDataPacket(serverAddress, port, blockNumber, buffer, bytesRead);
                // Sends packet through the socket
                socket.send(dataPacket);
                System.out.println("Sent data packet with block number: " + blockNumber);
                blockNumber++;
            }
        }
    }

    public static void retrieveFile(DatagramSocket socket, InetAddress serverAddress, int portNumber, String fileName) {
        try {
            // Creates the request data packet
            byte[] requestData = new byte[fileName.length() + 2];
            // Setting the opcode to RRQ (0x0001)
            requestData[0] = 0;
            requestData[1] = 1;
            // Copy the filename into the request data packet
            System.arraycopy(fileName.getBytes(), 0, requestData, 2, fileName.length());

            // Sends the request data packet to the server
            DatagramPacket sendPacket = new DatagramPacket(requestData, requestData.length, serverAddress, portNumber);
            socket.send(sendPacket);
            System.out.println("Sent packet to " + serverAddress + ":" + portNumber);

            // Receiving the file data from the server
            byte[] buffer = new byte[516]; //516 bytes large
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            FileOutputStream fileOutput = new FileOutputStream(fileName);
            short blockNumber = 0;
            int numAcksSent = 0;
            while (true) {
                // Waits for a packet to arrive from the server
                socket.receive(receivePacket);
                System.out.println("Received packet from " + receivePacket.getAddress() + ":" + receivePacket.getPort());

                byte[] data = receivePacket.getData();
                // Checks the opcode to see if this is a data packet (opcode 0x0003)
                int opcode = ((data[0] << 8) | data[1]) & 0xffff;
                if (opcode == 3) {
                    // Extracts the block number from the data packet
                    short receivedBlockNumber = (short) (((data[2] << 8) | data[3]) & 0xffff);
                    if (blockNumber == receivedBlockNumber) {
                        // Writes the data load to the output file
                        fileOutput.write(data, 4, receivePacket.getLength() - 4);
                        System.out.println("Received block " + blockNumber);
                        // Creates and send an acknowledgement packet back to the server
                        DatagramPacket ackPacket = createAckPacket(serverAddress, portNumber, blockNumber);
                        socket.send(ackPacket);
                        System.out.println("Ack packet " + numAcksSent + " sent");
                        numAcksSent++;
                        blockNumber++;
                        // if this is the last packet (less than 516 bytes of data) then breaks the while loop
                        if (receivePacket.getLength() < 516) {
                            break;
                        }
                    }
                } else if (opcode == 5) {
                    // If this is an error packet, prints the error message and stops the loop
                    System.out.println("Error: " + new String(data, 4, receivePacket.getLength() - 4));
                    break;
                }
            }
            // Close the output file stream
            fileOutput.close();
        } catch (IOException e) { // Exception handler
            System.out.println("Error retrieving file: " + e.getMessage());
        }
    }

    // Extracts the opcode from a given packet
    public static short getOpcode(DatagramPacket packet) {
        byte[] data = packet.getData();
        return ByteBuffer.wrap(data).getShort();
    }

}
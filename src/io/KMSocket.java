/*******************************************************************************
 * Copyright (C) 2013 University of Waikato, Hamilton, New Zealand
 ******************************************************************************/
package io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * An abstract socket connection to a port. Contains connection, IO methods, and
 * closing.
 * 
 * @author Sam Sarjant
 */
public abstract class KMSocket {
	/** Socket timeout. */
	public static final int SOCKET_TIMEOUT = -1;

	/** The localhost name for SSH tunnelling connections. */
	public static final String LOCALHOST = "localhost";

	/** A counter for command accesses. */
	private long commandCount_ = 0;

	/** The input stream (output from WMI). */
	private BufferedReader in_;

	/** The output stream (input to WMI). */
	private PrintWriter out_;

	/** The socket to connect to. */
	private Socket socket_;

	/** The access point controlling this socket. */
	protected KMAccess<? extends KMSocket> access_;

	/** The port to connect to. */
	private int port_;

	/** If the socket can be restarted. */
	protected boolean canRestart_;

	/**
	 * Restarts the connection by closing this socket and opening a new one.
	 * Returns false if no commands have been issued since the last restart
	 * (i.e. to avoid infinitely new connections).
	 * 
	 * @return True if the socket has processed at least one command since last
	 *         reopening. False otherwise, or if there is an exception.
	 */
	protected boolean restartConnection() {
		if (!canRestart_)
			return false;
		
		try {
			disconnect();
			connect();
			canRestart_ = false;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Constructor for a new KMSocket
	 */
	public KMSocket(KMAccess<? extends KMSocket> access) {
		this(access, -1);
	}

	public KMSocket(KMAccess<? extends KMSocket> access, int port) {
		try {
			port_ = port;
			access_ = access;
			connect();
		} catch (Exception e) {
			System.err.println("Could not connect to socket ("
					+ getMachineName() + ":" + getPort() + ")");
			e.printStackTrace();
		}
	}

	/**
	 * Closes the socket.
	 */
	public void close() {
		try {
			in_.close();
			out_.close();
			socket_.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Connects to WMI, either locally or via an SSH tunnel, depending on the
	 * current location of this executable.
	 * 
	 * @throws IOException
	 *             If there is trouble connecting to IO.
	 * @throws UnknownHostException
	 *             If the host is unavailable.
	 */
	protected void connect() throws UnknownHostException, IOException {
		if (socket_ != null) {
			disconnect();
		}
		socket_ = new Socket(getMachineName(), getPort());
		if (SOCKET_TIMEOUT > 0)
			socket_.setSoTimeout(SOCKET_TIMEOUT);

		out_ = new PrintWriter(socket_.getOutputStream(), true);
		in_ = new BufferedReader(new InputStreamReader(
				socket_.getInputStream(), "UTF-8"));
	}

	/**
	 * Disconnects from the socket.
	 * 
	 * @throws IOException
	 *             Should something go awry...
	 */
	public void disconnect() throws IOException {
		out_.close();
		in_.close();
		socket_.close();
	}

	protected int getPort() {
		return port_;
	}

	protected abstract String getMachineName();

	public long getCommandCount() {
		return commandCount_;
	}

	/**
	 * Queries the socket with the given input, returning some output.
	 * 
	 * @param input
	 *            The input query.
	 * @return the output query.
	 * @throws IOException
	 *             Should something go awry...
	 */
	public String querySocket(String input) {
		String output = null;
		try {
			out_.println(input);
			commandCount_++;
			output = in_.readLine().trim();
		} catch (Exception e) {
			e.printStackTrace();
		}
		canRestart_ = true;
		return output;
	}

	/**
	 * Reads a line from the input socket.
	 * 
	 * @return The line that was read.
	 * @throws Exception
	 *             Should something go awry...
	 */
	public String readLine() throws IOException {
		return in_.readLine();
	}

	/**
	 * Reads in remaining text from socket, using timeout exception to break.
	 */
	protected void readRemaining() {
		while (true) {
			try {
				System.err.println("READING REMAINING: " + in_.readLine());
			} catch (Exception e) {
				return;
			}
		}
	}

	/**
	 * Clears all cached articles.
	 */
	public void clearCachedArticles() {
		access_.clearCache();
	}
}

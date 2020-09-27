// Copyright (c) 2003-present, Jodd Team (http://jodd.org)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package jodd.http;

import jodd.io.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple HTTP tunnel base ready to be extended.
 */
public class HttpTunnel {

	/**
	 * The number of threads that can be executed in parallel.
	 */
	protected int threadPoolSize = 10;

	/**
	 * Number of incoming sockets connection that can be hold
	 * before processing each.
	 */
	protected int socketBacklog = 100;

	/**
	 * Tunnel listening port.
	 */
	protected int listenPort = 8888;

	/**
	 * Target host.
	 */
	protected String targetHost = "localhost";

	/**
	 * Target port.
	 */
	protected int targetPort = 8080;

	protected ExecutorService executorService;
	protected volatile boolean running;
	protected ServerSocket serverSocket;

	/**
	 * Starts HTTP tunnel. Method ends when the tunnel is stopped.
	 */
	public void start() throws IOException {
		serverSocket = new ServerSocket(listenPort, socketBacklog);
		serverSocket.setReuseAddress(true);
		executorService = Executors.newFixedThreadPool(threadPoolSize);

		running = true;
		while (running) {
			final Socket socket = serverSocket.accept();
			socket.setKeepAlive(false);
			executorService.execute(onSocketConnection(socket));
		}
		executorService.shutdown();
	}

	/**
	 * Invoked on incoming connection. By default returns {@link HttpTunnelConnection}
	 * to handle the connection. May be used to return custom
	 * handlers.
	 */
	protected Runnable onSocketConnection(final Socket socket) {
		return new HttpTunnelConnection(socket);
	}

	/**
	 * Stops the tunnel, shutdowns the thread pool and closes server socket.
	 */
	public void stop() {
		running = false;
		executorService.shutdown();
		try {
			serverSocket.close();
		} catch (final IOException ignore) {
		}
	}

	/**
	 * Single connection handler that performs the tunneling.
	 */
	public class HttpTunnelConnection implements Runnable {

		protected final Socket socket;

		public HttpTunnelConnection(final Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				tunnel();
			} catch (final IOException ioex) {
				ioex.printStackTrace();
			}
		}

		/**
		 * Invoked after income connection is parsed. Nothing is
		 * changed in the request, except the target host and port.
		 */
		protected void onRequest(final HttpRequest request) {
		}

		/**
		 * Invoked after target response is processed. Response is now
		 * ready to be sent back to the client. The following header
		 * parameters are changed:
		 * <ul>
		 * <li>Transfer-Encoding is removed, as body is returned at once,</li>
		 * <li>Content-Length is added/update to body size.</li>
		 * </ul>
		 */
		protected void onResponse(final HttpResponse response) {
		}

		/**
		 * Performs the tunneling. The following steps occurs:
		 * <ul>
		 * <li>read and parse clients request</li>
		 * <li>open socket to target</li>
		 * <li>resend request to target</li>
		 * <li>read targets response</li>
		 * <li>fix response and resend it to client</li>
		 * </ul>
		 */
		protected void tunnel() throws IOException {

			// read request
			final InputStream socketInput = socket.getInputStream();
			final HttpRequest request = HttpRequest.readFrom(socketInput);

			// open client socket to target
			final Socket clientSocket = Sockets.connect(targetHost, targetPort);

			// do request
			request.host(targetHost);
			request.port(targetPort);
			request.setHostHeader();
			onRequest(request);

			// resend request to target
			final OutputStream out = clientSocket.getOutputStream();
			request.sendTo(out);

			// read target response
			final InputStream in = clientSocket.getInputStream();
			final HttpResponse response = HttpResponse.readFrom(in);

			// close client socket
			IOUtil.close(in);
			IOUtil.close(out);
			try {
				clientSocket.close();
			} catch (final IOException ignore) {
			}

			// fix response
			if (response.bodyRaw() != null) {
				response.headerRemove("Transfer-Encoding");
				response.contentLength(response.bodyRaw().length());
			}

			// do response
			onResponse(response);

			// send response back
			final OutputStream socketOutput = socket.getOutputStream();
			response.sendTo(socketOutput);

			// close socket
			IOUtil.close(socketInput);
			IOUtil.close(socketOutput);
			try {
				socket.close();
			} catch (final IOException ignore) {
			}
		}
	}

}

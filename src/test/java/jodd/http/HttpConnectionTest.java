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

import jodd.http.upload.ByteArrayUploadable;
import jodd.io.FileUtil;
import jodd.net.MimeTypes;
import jodd.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpConnectionTest {

	@Test
	void testEcho() throws IOException {
		final EchoTestServer echoTestServer = new EchoTestServer();

		final HttpResponse response = HttpRequest.get("http://localhost:8081/hello?id=12").send();

		assertEquals(200, response.statusCode());
		assertEquals("OK", response.statusPhrase());

		assertEquals("GET", echoTestServer.method);
		assertEquals("/hello", echoTestServer.uri);
		assertEquals(1, echoTestServer.params.size());
		assertEquals("12", echoTestServer.params.get("id"));

		assertEquals("GET /hello", response.bodyRaw());

		echoTestServer.stop();
	}

	@Test
	void testUpload() throws IOException {
		final EchoTestServer echoTestServer = new EchoTestServer();

		final File file = FileUtil.createTempFile();
		file.deleteOnExit();

		FileUtil.writeString(file, "upload тест");
		assertEquals("upload тест", FileUtil.readString(file));

		final HttpResponse response = HttpRequest
				.post("http://localhost:8081/hello")
				.form("id", "12")
				.form("file", file)
				.send();

		assertEquals(200, response.statusCode());
		assertEquals("OK", response.statusPhrase());

		assertEquals("POST", echoTestServer.method);
		assertEquals("12", echoTestServer.params.get("id"));
		final File uploadedFile = new File(echoTestServer.files.get("file").toString());
		assertNotNull(uploadedFile);
		assertEquals("upload тест", FileUtil.readString(uploadedFile));

		assertEquals("POST /hello", response.bodyRaw());

		echoTestServer.stop();
		file.delete();
	}

	@Test
	void testUploadWithUploadable() throws IOException {
		final EchoTestServer echoTestServer = new EchoTestServer();

		final HttpResponse response = HttpRequest
				.post("http://localhost:8081/hello")
				.multipart(true)
				.form("id", "12")
				.form("file", new ByteArrayUploadable(
					"upload тест".getBytes(StandardCharsets.UTF_8), "d ст", MimeTypes.MIME_TEXT_PLAIN))
				.send();

		assertEquals(200, response.statusCode());
		assertEquals("OK", response.statusPhrase());

		assertEquals("POST", echoTestServer.method);
		assertEquals("12", echoTestServer.params.get("id"));
		final File uploadedFile = new File(echoTestServer.files.get("file").toString());
		assertNotNull(uploadedFile);
		assertEquals("upload тест", FileUtil.readString(uploadedFile));

		assertEquals("POST /hello", response.bodyRaw());

		echoTestServer.stop();
	}

	@Test
	void testUploadWithMonitor() throws IOException {
		final EchoTestServer echoTestServer = new EchoTestServer();

		final File file = FileUtil.createTempFile();
		file.deleteOnExit();

		FileUtil.writeString(file, StringUtil.repeat('A', 1024));

		final StringBuilder sb = new StringBuilder();

		final HttpResponse response = HttpRequest
				.post("http://localhost:8081/hello")
				.form("id", "12")
				.form("file", file)
				.monitor(new HttpProgressListener() {
					@Override
					public void transferred(final int len) {
						sb.append(":").append(len);
					}
				})
				.send();

		assertEquals(200, response.statusCode());
		assertEquals("OK", response.statusPhrase());

		echoTestServer.stop();
		file.delete();

		assertEquals(":0:512:1024:148", StringUtil.substring(sb.toString(), 0, -1));
	}

}

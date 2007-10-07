/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.RedirectException;
import org.apache.http.client.RoutedRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.SM;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Redirection test cases.
 *
 * @author Oleg Kalnichevski
 * 
 * @version $Revision$
 */
public class TestRedirects extends ServerTestBase {

    // ------------------------------------------------------------ Constructor
    public TestRedirects(final String testName) throws IOException {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestRedirects.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestRedirects.class);
    }

    private class BasicRedirectService implements HttpRequestHandler {
        
		private int statuscode = HttpStatus.SC_MOVED_TEMPORARILY;
		private String host = null;
        private int port;

        public BasicRedirectService(final String host, int port, int statuscode) {
            super();
            this.host = host;
            this.port = port;
            if (statuscode > 0) {
            	this.statuscode = statuscode;
            }
        }

        public BasicRedirectService(final String host, int port) {
            this(host, port, -1);
        }

        public void handle(
                final HttpRequest request, 
                final HttpResponse response, 
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.equals("/oldlocation/")) {
                response.setStatusLine(ver, this.statuscode);
                response.addHeader(new BasicHeader("Location", 
                		"http://" + this.host + ":" + this.port + "/newlocation/"));
                response.addHeader(new BasicHeader("Connection", "close"));
            } else if (uri.equals("/newlocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private class CircularRedirectService implements HttpRequestHandler {

        private int invocations = 0;
        
        public CircularRedirectService() {
            super();
        }
        
        public void handle(
                final HttpRequest request, 
                final HttpResponse response, 
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.startsWith("/circular-oldlocation")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/circular-location2?invk=" + (++this.invocations)));
            } else if (uri.startsWith("/circular-location2")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", "/circular-oldlocation?invk=" + (++this.invocations)));
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    private class RelativeRedirectService implements HttpRequestHandler {
        
            public RelativeRedirectService() {
                super();
            }

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                String uri = request.getRequestLine().getUri();
                if (uri.equals("/oldlocation/")) {
                    response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/relativelocation/"));
                } else if (uri.equals("/relativelocation/")) {
                    response.setStatusLine(ver, HttpStatus.SC_OK);
                    StringEntity entity = new StringEntity("Successful redirect");
                    response.setEntity(entity);
                } else {
                    response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
                }
            }
        }

    private class BogusRedirectService implements HttpRequestHandler {
        private String url;
        
        public BogusRedirectService(String redirectUrl) {
            super();
            this.url = redirectUrl;
        }

        public void handle(
                final HttpRequest request, 
                final HttpResponse response, 
                final HttpContext context) throws HttpException, IOException {
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            String uri = request.getRequestLine().getUri();
            if (uri.equals("/oldlocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_MOVED_TEMPORARILY);
                response.addHeader(new BasicHeader("Location", url));
            } else if (uri.equals("/relativelocation/")) {
                response.setStatusLine(ver, HttpStatus.SC_OK);
                StringEntity entity = new StringEntity("Successful redirect");
                response.setEntity(entity);
            } else {
                response.setStatusLine(ver, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    public void testBasicRedirect300() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", 
                new BasicRedirectService(host, port, HttpStatus.SC_MULTIPLE_CHOICES));
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        
        assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getStatusLine().getStatusCode());
        assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
    }

    public void testBasicRedirect301() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", 
                new BasicRedirectService(host, port, HttpStatus.SC_MOVED_PERMANENTLY));
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost targetHost = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        assertEquals(host, targetHost.getHostName());
        assertEquals(port, targetHost.getPort());
    }

    public void testBasicRedirect302() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", 
                new BasicRedirectService(host, port, HttpStatus.SC_MOVED_TEMPORARILY));
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost targetHost = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        assertEquals(host, targetHost.getHostName());
        assertEquals(port, targetHost.getPort());
    }

    public void testBasicRedirect303() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", 
                new BasicRedirectService(host, port, HttpStatus.SC_SEE_OTHER));
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost targetHost = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        assertEquals(host, targetHost.getHostName());
        assertEquals(port, targetHost.getPort());
    }

    public void testBasicRedirect304() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", 
                new BasicRedirectService(host, port, HttpStatus.SC_NOT_MODIFIED));
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        
        assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getStatusLine().getStatusCode());
        assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
    }

    public void testBasicRedirect305() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", 
                new BasicRedirectService(host, port, HttpStatus.SC_USE_PROXY));
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        
        assertEquals(HttpStatus.SC_USE_PROXY, response.getStatusLine().getStatusCode());
        assertEquals("/oldlocation/", reqWrapper.getRequestLine().getUri());
    }

    public void testBasicRedirect307() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", 
                new BasicRedirectService(host, port, HttpStatus.SC_TEMPORARY_REDIRECT));
        
        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }

        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost targetHost = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        assertEquals(host, targetHost.getHostName());
        assertEquals(port, targetHost.getPort());
    }

    public void testMaxRedirectCheck() throws Exception {
        this.localServer.register("*", new CircularRedirectService());

        DefaultHttpClient client = new DefaultHttpClient(); 
        client.getParams().setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
        client.getParams().setIntParameter(ClientPNames.MAX_REDIRECTS, 5);

        HttpGet httpget = new HttpGet("/circular-oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute());
        try {
            client.execute(request);
            fail("RedirectException exception should have been thrown");
        } catch (RedirectException e) {
            // expected
        }
    }

    public void testCircularRedirect() throws Exception {
        this.localServer.register("*", new CircularRedirectService());

        DefaultHttpClient client = new DefaultHttpClient(); 
        client.getParams().setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, false);

        HttpGet httpget = new HttpGet("/circular-oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute());
        try {
            client.execute(request);
            fail("CircularRedirectException exception should have been thrown");
        } catch (CircularRedirectException e) {
            // expected
        }
    }

    public void testPostRedirect() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", new BasicRedirectService(host, port));

        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();
        
        HttpPost httppost = new HttpPost("/oldlocation/");
        httppost.setEntity(new StringEntity("stuff"));

        RoutedRequest request = new RoutedRequest.Impl(httppost, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }
        
        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());
        assertEquals("GET", reqWrapper.getRequestLine().getMethod());
    }

    public void testRelativeRedirect() throws Exception {
        int port = this.localServer.getServicePort();
        String host = "localhost";
        this.localServer.register("*", new RelativeRedirectService());

        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        client.getParams().setBooleanParameter(
                ClientPNames.REJECT_RELATIVE_REDIRECT, false);
        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute()); 
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }
        
        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);
        HttpHost targetHost = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/relativelocation/", reqWrapper.getRequestLine().getUri());
        assertEquals(host, targetHost.getHostName());
        assertEquals(port, targetHost.getPort());
    }

    public void testRejectRelativeRedirect() throws Exception {
        this.localServer.register("*", new RelativeRedirectService());

        DefaultHttpClient client = new DefaultHttpClient(); 

        client.getParams().setBooleanParameter(
                ClientPNames.REJECT_RELATIVE_REDIRECT, true);
        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute());
        try {
            client.execute(request);
            fail("ProtocolException exception should have been thrown");
        } catch (ProtocolException e) {
            // expected
        }
    }

    public void testRejectBogusRedirectLocation() throws Exception {
        this.localServer.register("*", new BogusRedirectService("xxx://bogus"));

        DefaultHttpClient client = new DefaultHttpClient(); 

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute());
        try {
            client.execute(request);
            fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public void testRejectInvalidRedirectLocation() throws Exception {
        String host = "localhost";
        int port = this.localServer.getServicePort();
        this.localServer.register("*", 
                new BogusRedirectService("http://"+ host +":"+ port +"/newlocation/?p=I have spaces"));

        DefaultHttpClient client = new DefaultHttpClient(); 

        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute());
        try {
            client.execute(request);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException e) {
            // expected
        }
    }

    public void testRedirectWithCookie() throws Exception {
        String host = "localhost";
        int port = this.localServer.getServicePort();

        this.localServer.register("*", 
                new BasicRedirectService(host, port));

        DefaultHttpClient client = new DefaultHttpClient(); 
        
        CookieStore cookieStore = new BasicCookieStore();
        client.setCookieStore(cookieStore);
        
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain("localhost");
        cookie.setPath("/");
        
        cookieStore.addCookie(cookie);

        HttpContext context = client.getDefaultContext();
        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute());
        
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }
        
        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());

        Header[] headers = reqWrapper.getHeaders(SM.COOKIE);
        assertEquals("There can only be one (cookie)", 1, headers.length);            
    }

    public void testDefaultHeadersRedirect() throws Exception {
        String host = "localhost";
        int port = this.localServer.getServicePort();

        this.localServer.register("*", 
                new BasicRedirectService(host, port));

        DefaultHttpClient client = new DefaultHttpClient(); 
        HttpContext context = client.getDefaultContext();

        List defaultHeaders = new ArrayList(1);
        defaultHeaders.add(new BasicHeader(HTTP.USER_AGENT, "my-test-client"));
        
        client.getParams().setParameter(ClientPNames.DEFAULT_HEADERS, defaultHeaders);
        
        HttpGet httpget = new HttpGet("/oldlocation/");

        RoutedRequest request = new RoutedRequest.Impl(httpget, getDefaultRoute());
        
        HttpResponse response = client.execute(request, context);
        HttpEntity e = response.getEntity();
        if (e != null) {
            e.consumeContent();
        }
        
        HttpRequest reqWrapper = (HttpRequest) context.getAttribute(
                ExecutionContext.HTTP_REQUEST);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals("/newlocation/", reqWrapper.getRequestLine().getUri());

        Header header = reqWrapper.getFirstHeader(HTTP.USER_AGENT);
        assertEquals("my-test-client", header.getValue());            
    }

}

package org.opencds.cqf;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.data.JpaDataProviderStu3;
import org.opencds.cqf.servlet.BaseServlet;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Scanner;

class TestServer {

    private FhirContext ourCtx = FhirContext.forDstu3();
    private Server ourServer;
    private String ourServerBase;
    JpaDataProviderStu3 dataProvider;
    int ourPort;
    IGenericClient ourClient;

    void start() throws Exception {
        String path = Paths.get("").toAbsolutePath().toString();

        // changing from random to hard coded
        ourPort = 8080;
        ourServer = new Server(ourPort);

        WebAppContext webAppContext = new WebAppContext();
        webAppContext.setContextPath("/cqf-ruler");
        webAppContext.setDescriptor(path + "/src/main/webapp/WEB-INF/web.xml");
        webAppContext.setResourceBase(path + "/target/cqf-ruler");
        webAppContext.setParentLoaderPriority(true);

        ourServer.setHandler(webAppContext);
        ourServer.start();

        Collection<IResourceProvider> resourceProviders = null;
        for (ServletHolder servletHolder : webAppContext.getServletHandler().getServlets()) {
            if (servletHolder.getServlet() instanceof BaseServlet) {
                resourceProviders = ((BaseServlet) servletHolder.getServlet()).getResourceProviders();
                break;
            }
        }

        dataProvider = new JpaDataProviderStu3(resourceProviders);

        ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
        ourServerBase = "http://localhost:" + ourPort + "/cqf-ruler/baseDstu3";
        ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
        ourClient.registerInterceptor(new LoggingInterceptor(true));
    }

    void stop() throws Exception {
        ourServer.stop();
    }

    void putResource(String resourceFileName, String id) {
        InputStream is = TestBase.class.getResourceAsStream(resourceFileName);
        Scanner scanner = new Scanner(is).useDelimiter("\\A");
        String json = scanner.hasNext() ? scanner.next() : "";

        boolean isJson = resourceFileName.endsWith("json");

        IBaseResource resource = isJson ? ourCtx.newJsonParser().parseResource(json) : ourCtx.newXmlParser().parseResource(json);

        if (resource instanceof Bundle) {
            ourClient.transaction().withBundle((Bundle) resource).execute();
        }
        else {
            ourClient.update().resource(resource).withId(id).execute();
        }
    }
}

package org.opencds.cqf.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.entity.ContentType;
import org.hl7.fhir.dstu3.model.PlanDefinition;
import org.opencds.cqf.cdshooks.providers.CdsHooksProviders;
import org.opencds.cqf.cdshooks.providers.Discovery;
import org.opencds.cqf.cdshooks.providers.DiscoveryItem;
import org.opencds.cqf.cdshooks.request.CdsRequest;
import org.opencds.cqf.cdshooks.request.CdsRequestFactory;
import org.opencds.cqf.cdshooks.response.CdsCard;
import org.opencds.cqf.providers.FHIRPlanDefinitionResourceProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@WebServlet(name = "cds-services")
public class CdsServicesServlet extends BaseServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // validate that we are dealing with JSON
            if (!request.getContentType().startsWith("application/json")) {
                throw new ServletException(String.format("Invalid content type %s. Please use application/json.", request.getContentType()));
            }
            String baseUrl =
                    request.getRequestURL().toString()
                            .replace(request.getPathInfo(), "").replace(request.getServletPath(), "") + "/baseDstu3";
            CdsHooksProviders cdsHooksProviders = new CdsHooksProviders(manager, baseUrl, request.getPathInfo().replace("/", ""));
            // TODO - check for cdc-opioid-guidance base call - runs every recommendation
            CdsRequest cdsRequest = CdsRequestFactory.createRequest(request.getReader());
            response.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
            response.getWriter().println(toJsonResponse(cdsRequest.process(cdsHooksProviders)));
        } catch (Exception e) {
            e.printStackTrace();
            response.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
            response.getWriter().println(toJsonResponse(Collections.singletonList(CdsCard.errorCard(e))));
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!request.getRequestURL().toString().endsWith("cds-services")) {
            throw new ServletException("This servlet is not configured to handle GET requests.");
        }

        JsonObject responseJson = new JsonObject();
        JsonArray services = new JsonArray();

        FHIRPlanDefinitionResourceProvider provider = (FHIRPlanDefinitionResourceProvider) getProvider("PlanDefinition");
        for (Discovery discovery : provider.getDiscoveries()) {
            PlanDefinition planDefinition = discovery.getPlanDefinition();
            JsonObject service = new JsonObject();
            if (planDefinition.hasAction()) {
                // TODO - this needs some work - too naive
                if (planDefinition.getActionFirstRep().hasTriggerDefinition()) {
                    if (planDefinition.getActionFirstRep().getTriggerDefinitionFirstRep().hasEventName()) {
                        service.addProperty("hook", planDefinition.getActionFirstRep().getTriggerDefinitionFirstRep().getEventName());
                    }
                }
            }
            if (planDefinition.hasName()) {
                service.addProperty("name", planDefinition.getName());
            }
            if (planDefinition.hasTitle()) {
                service.addProperty("title", planDefinition.getTitle());
            }
            if (planDefinition.hasDescription()) {
                service.addProperty("description", planDefinition.getDescription());
            }
            service.addProperty("id", planDefinition.getIdElement().getIdPart());

            if (!discovery.getItems().isEmpty()) {
                JsonObject prefetchContent = new JsonObject();
                for (DiscoveryItem item : discovery.getItems()) {
                    prefetchContent.addProperty(item.getItemNo(), item.getUrl());
                }
                service.add("prefetch", prefetchContent);
            }
            services.add(service);
        }

        responseJson.add("services", services);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        response.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        response.getWriter().println(gson.toJson(responseJson));
    }

    private String toJsonResponse(List<CdsCard> cards) {
        JsonObject ret = new JsonObject();
        JsonArray cardArray = new JsonArray();

        for (CdsCard card : cards) {
            cardArray.add(card.toJson());
        }

        ret.add("cards", cardArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return  gson.toJson(ret);
    }
}

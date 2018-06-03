package org.opencds.cqf.cdshooks.providers;

import ca.uhn.fhir.jpa.rp.dstu3.CodeSystemResourceProvider;
import ca.uhn.fhir.jpa.rp.dstu3.LibraryResourceProvider;
import ca.uhn.fhir.jpa.rp.dstu3.ValueSetResourceProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.*;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.PlanDefinition;
import org.hl7.fhir.instance.model.Bundle;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IIdType;
import org.opencds.cqf.config.STU3LibraryLoader;
import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderDstu2;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.providers.FHIRPlanDefinitionResourceProvider;
import org.opencds.cqf.providers.JpaDataProvider;
import org.opencds.cqf.providers.JpaTerminologyProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CdsHooksProviders {

    private JpaDataProvider jpaDataProvider;
    public JpaDataProvider getJpaDataProvider() {
        return jpaDataProvider;
    }

    private Library library;

    private IGenericClient client;
    public boolean hasClient() {
        return client != null;
    }
    public IGenericClient getClient() {
        return client;
    }

    private FHIRPlanDefinitionResourceProvider planDefinitionProvider;
    public FHIRPlanDefinitionResourceProvider getPlanDefinitionProvider() {
        return planDefinitionProvider;
    }

    private Discovery discovery;
    public Discovery getDiscovery() {
        return discovery;
    }

    private PlanDefinition planDefinition;
    public PlanDefinition getPlanDefinition() {
        return planDefinition;
    }

    private Context context;
    public Context getContext() {
        return context;
    }

    // todo - STU4 support
    public enum FhirVersion { DSTU2, DSTU3 }

    private FhirVersion version;
    public FhirVersion getVersion() {
        return version;
    }

    public boolean isDstu2() {
        return version == FhirVersion.DSTU2;
    }

    public CdsHooksProviders(JpaDataProvider jpaDataProvider, String service) {
        this.jpaDataProvider = jpaDataProvider;

        // resolve library loader
        STU3LibraryLoader libraryLoader = new STU3LibraryLoader(
                (LibraryResourceProvider) jpaDataProvider.resolveResourceProvider("Library"),
                new LibraryManager(new ModelManager()), new ModelManager()
        );

        // resolve plan definition
        planDefinitionProvider = (FHIRPlanDefinitionResourceProvider) jpaDataProvider.resolveResourceProvider("PlanDefinition");
        planDefinition = planDefinitionProvider.getDao().read(new IdType(service));
        if (planDefinition == null) {
            throw new IllegalArgumentException("Could not find PlanDefinition/" + service);
        }

        // resolve prefetch urls
        discovery = planDefinitionProvider.getDiscovery(planDefinition);

        // resolve library
        if (planDefinition.hasLibrary()) {
            IIdType libraryId = planDefinition.getLibraryFirstRep().getReferenceElement();
            if (libraryId.hasVersionIdPart()) {
                library = libraryLoader.load(
                        new VersionedIdentifier().withId(libraryId.getIdPart()).withVersion(libraryId.getVersionIdPart())
                );
            }
            else {
                library = libraryLoader.load(new VersionedIdentifier().withId(libraryId.getIdPart()));
            }
        }
        else {
            throw new IllegalArgumentException("Missing library reference for PlanDefinition/" + service);
        }

        // resolve version
        if (library.getUsings() == null) {
            throw new RuntimeException("The library doesn't specify a model");
        }
        for (UsingDef model : library.getUsings().getDef()) {
            if (model.getLocalIdentifier().equals("FHIR")) {
                if (model.getVersion() == null) {
                    // default
                    version = FhirVersion.DSTU3;
                }
                if (model.getVersion().equals("1.0.2")) {
                    version = FhirVersion.DSTU2;
                }
                // TODO - STU4 support
                else if (model.getVersion().equals("3.2.0")) {
                    throw new RuntimeException("FHIR version 3.2.0 is currently not supported");
                }
                else {
                    version = FhirVersion.DSTU3;
                }
            }
        }
        if (version == null) {
            throw new RuntimeException("The library must use the FHIR model");
        }

        // resolve context
        context = new Context(library);
        // default providers/loaders
        context.registerDataProvider("http://hl7.org/fhir", jpaDataProvider);
        context.registerTerminologyProvider(jpaDataProvider.getTerminologyProvider());
        context.registerLibraryLoader(libraryLoader);
        context.setExpressionCaching(true);
    }

    public void resolveContextParameter(List<Object> resources) {
        // set ContextPrescriptions parameter
        // TODO - I don't know about this...
        if (library.getParameters() != null) {
            for (ParameterDef params : library.getParameters().getDef()) {
                if (params.getParameterTypeSpecifier() instanceof ListTypeSpecifier) {
                    context.setParameter(null, params.getName(), resources);
                }
            }
        }
    }

    private void registerDataProvider(DataProvider provider, String modelUri) {
        BaseFhirDataProvider dataProvider = (BaseFhirDataProvider) provider;
        // TODO - authenticate fhir auth if provided in request
        if (modelUri == null) {
            modelUri = "http://hl7.org/fhir";
        }
        dataProvider.getFhirContext().getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        dataProvider.setTerminologyProvider(jpaDataProvider.getTerminologyProvider());
        client = dataProvider.getFhirClient();
        context.registerDataProvider(modelUri, dataProvider);
    }

    public DiscoveryDataProvider getDiscoveryProvider() {
        return isDstu2() ? new DiscoveryDataProviderDstu2() : new DiscoveryDataProviderStu3();
    }

    public void resolveRemoteDataProvider(String fhirServerEndpoint) {
        // data provider will be configured for the same FHIR version as the library
        if (isDstu2()) {
            registerDataProvider(new FhirDataProviderDstu2().setEndpoint(fhirServerEndpoint), null);
        }
        else {
            registerDataProvider(new FhirDataProviderStu3().setEndpoint(fhirServerEndpoint), null);
        }
    }

    public void resolvePrefetchDataProvider(List<Object> resources) {
        registerDataProvider(isDstu2() ? new PrefetchDataProviderDstu2(resources) : new PrefetchDataProviderStu3(resources), null);
    }

    public JsonObject nullifyPrefetch() {
        JsonObject nullPrefetch = new JsonObject();
        for (DiscoveryItem item : getDiscovery().getItems()) {
            nullPrefetch.add(item.getItemNo(), JsonNull.INSTANCE);
        }
        return nullPrefetch;
    }

    public List<Object> search(DiscoveryItem discoveryItem, String patientId) {
        IGenericClient gClient = hasClient() ? client : jpaDataProvider.getFhirClient();
        IQuery<IBaseBundle> query = gClient.search().forResource(discoveryItem.getResource());
        if (discoveryItem.isPatientCriteria()) {
            query = query.where(new TokenClientParam(discoveryItem.getPatientPath()).exactly().identifier(patientId));
        }
        for (int i = 0; i < discoveryItem.getCriteria().size(); ++i) {
            query = i == 0 && !discoveryItem.isPatientCriteria()
                    ? query.where(discoveryItem.getCriteria().get(i))
                    : query.and(discoveryItem.getCriteria().get(i));
        }

        return isDstu2() ? dstu2Search(query) : stu3Search(query);
    }

    private List<Object> dstu2Search(IQuery<IBaseBundle> query) {
        Bundle bundle = query.returnBundle(Bundle.class).execute();
        List<Object> resources = new ArrayList<>();
        if (bundle.hasEntry()) {
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.hasResource()) {
                    resources.add(entry.getResource());
                }
            }
        }
        return resources;
    }

    private List<Object> stu3Search(IQuery<IBaseBundle> query) {
        org.hl7.fhir.dstu3.model.Bundle bundle = query.returnBundle(org.hl7.fhir.dstu3.model.Bundle.class).execute();
        List<Object> resources = new ArrayList<>();
        if (bundle.hasEntry()) {
            for (org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.hasResource()) {
                    resources.add(entry.getResource());
                }
            }
        }
        return resources;
    }
}

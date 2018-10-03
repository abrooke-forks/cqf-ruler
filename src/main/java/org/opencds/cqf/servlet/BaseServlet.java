package org.opencds.cqf.servlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.dao.dstu3.FhirSystemDaoDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaSystemProviderDstu3;
import ca.uhn.fhir.jpa.rp.dstu3.*;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import ca.uhn.fhir.jpa.term.IHapiTerminologySvcDstu3;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.IServerInterceptor;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Meta;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.interceptors.TransactionInterceptor;
import org.opencds.cqf.providers.*;
import org.springframework.http.HttpMethod;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;

import javax.servlet.ServletException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by Chris Schuler on 12/11/2016.
 */
public class BaseServlet extends RestfulServer {

    private JpaDataProvider provider;
    public JpaDataProvider getProvider() {
        return provider;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void initialize() throws ServletException {

        super.initialize();

        FhirVersionEnum fhirVersion = FhirVersionEnum.DSTU3;
        setFhirContext(new FhirContext(fhirVersion));

        // Get the spring context from the web container (it's declared in web.xml)
        WebApplicationContext myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();

        if (myAppCtx == null) {
            throw new ServletException("WebApplicationContext is null");
        }

        String resourceProviderBeanName = "myResourceProvidersDstu3";
        List<IResourceProvider> beans = myAppCtx.getBean(resourceProviderBeanName, List.class);
        setResourceProviders(beans);

        Object systemProvider = myAppCtx.getBean("mySystemProviderDstu3", JpaSystemProviderDstu3.class);
        setPlainProviders(systemProvider);

        IFhirSystemDao<Bundle, Meta> systemDao = myAppCtx.getBean("mySystemDaoDstu3", IFhirSystemDao.class);
        JpaConformanceProviderDstu3 confProvider = new JpaConformanceProviderDstu3(this, systemDao,
                myAppCtx.getBean(DaoConfig.class));
        confProvider.setImplementationDescription("Measure and Opioid Processing Server");
        setServerConformanceProvider(confProvider);

        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);
        setPagingProvider(myAppCtx.getBean(DatabaseBackedPagingProvider.class));

        /*
		 * Load interceptors for the server from Spring (these are defined in FhirServerConfig.java)
		 */
        Collection<IServerInterceptor> interceptorBeans = myAppCtx.getBeansOfType(IServerInterceptor.class).values();
        for (IServerInterceptor interceptor : interceptorBeans) {
            this.registerInterceptor(interceptor);
        }

        provider = new JpaDataProvider(getResourceProviders());
        TerminologyProvider terminologyProvider = new JpaTerminologyProvider(myAppCtx.getBean("terminologyService", IHapiTerminologySvcDstu3.class), getFhirContext(), (ValueSetResourceProvider) provider.resolveResourceProvider("ValueSet"));
        provider.setTerminologyProvider(terminologyProvider);

        resolveResourceProviders(provider);

        // Register the logging interceptor
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        this.registerInterceptor(loggingInterceptor);

        // The SLF4j logger "test.accesslog" will receive the logging events
        loggingInterceptor.setLoggerName("logging.accesslog");

        // This is the format for each line. A number of substitution variables may
        // be used here. See the JavaDoc for LoggingInterceptor for information on
        // what is available.
        loggingInterceptor.setMessageFormat("Source[${remoteAddr}] Operation[${operationType} ${idOrResourceName}] UA[${requestHeader.user-agent}] Params[${requestParameters}]");

        //setServerAddressStrategy(new HardcodedServerAddressStrategy("http://mydomain.com/fhir/baseDstu2"));
        //registerProvider(myAppCtx.getBean(TerminologyUploaderProviderDstu3.class));

        // TODO - will need this for Measure/$submit-data operation
//        FhirSystemDaoDstu3 systemDaoDstu3 = myAppCtx.getBean("mySystemDaoDstu3", FhirSystemDaoDstu3.class);

        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedHeader("x-fhir-starter");
        config.addAllowedHeader("Origin");
        config.addAllowedHeader("Accept");
        config.addAllowedHeader("X-Requested-With");
        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("Cache-Control");

        config.addAllowedOrigin("*");

        config.addExposedHeader("Location");
        config.addExposedHeader("Content-Location");
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Create the interceptor and register it
        CorsInterceptor interceptor = new CorsInterceptor(config);
        registerInterceptor(interceptor);
    }

    private void resolveResourceProviders(JpaDataProvider provider) throws ServletException {
        // Bundle processing
        FHIRBundleResourceProvider bundleProvider = new FHIRBundleResourceProvider(provider);
        BundleResourceProvider jpaBundleProvider = (BundleResourceProvider) provider.resolveResourceProvider("Bundle");
        bundleProvider.setDao(jpaBundleProvider.getDao());
        bundleProvider.setContext(jpaBundleProvider.getContext());

        try {
            unregister(jpaBundleProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(bundleProvider, provider.getCollectionProviders());

        // ValueSet processing
        FHIRValueSetResourceProvider valueSetProvider = new FHIRValueSetResourceProvider(provider);
        ValueSetResourceProvider jpaValueSetProvider = (ValueSetResourceProvider) provider.resolveResourceProvider("ValueSet");
        valueSetProvider.setDao(jpaValueSetProvider.getDao());
        valueSetProvider.setContext(jpaValueSetProvider.getContext());

        try {
            unregister(jpaValueSetProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(valueSetProvider, provider.getCollectionProviders());
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor(valueSetProvider);
        registerInterceptor(transactionInterceptor);

        // Measure processing
        FHIRMeasureResourceProvider measureProvider = new FHIRMeasureResourceProvider(provider);
        MeasureResourceProvider jpaMeasureProvider = (MeasureResourceProvider) provider.resolveResourceProvider("Measure");
        measureProvider.setDao(jpaMeasureProvider.getDao());
        measureProvider.setContext(jpaMeasureProvider.getContext());

        try {
            unregister(jpaMeasureProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(measureProvider, provider.getCollectionProviders());

        // ActivityDefinition processing
        FHIRActivityDefinitionResourceProvider actDefProvider = new FHIRActivityDefinitionResourceProvider(provider);
        ActivityDefinitionResourceProvider jpaActDefProvider = (ActivityDefinitionResourceProvider) provider.resolveResourceProvider("ActivityDefinition");
        actDefProvider.setDao(jpaActDefProvider.getDao());
        actDefProvider.setContext(jpaActDefProvider.getContext());

        try {
            unregister(jpaActDefProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(actDefProvider, provider.getCollectionProviders());

        // PlanDefinition processing
        FHIRPlanDefinitionResourceProvider planDefProvider = new FHIRPlanDefinitionResourceProvider(provider);
        PlanDefinitionResourceProvider jpaPlanDefProvider = (PlanDefinitionResourceProvider) provider.resolveResourceProvider("PlanDefinition");
        planDefProvider.setDao(jpaPlanDefProvider.getDao());
        planDefProvider.setContext(jpaPlanDefProvider.getContext());

        try {
            unregister(jpaPlanDefProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(planDefProvider, provider.getCollectionProviders());

        // StructureMap processing
        FHIRStructureMapResourceProvider structureMapProvider = new FHIRStructureMapResourceProvider(provider);
        StructureMapResourceProvider jpaStructMapProvider = (StructureMapResourceProvider) provider.resolveResourceProvider("StructureMap");
        structureMapProvider.setDao(jpaStructMapProvider.getDao());
        structureMapProvider.setContext(jpaStructMapProvider.getContext());

        try {
            unregister(jpaStructMapProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(structureMapProvider, provider.getCollectionProviders());

        // Patient processing - for bulk data export
        BulkDataPatientProvider bulkDataPatientProvider = new BulkDataPatientProvider(provider);
        PatientResourceProvider jpaPatientProvider = (PatientResourceProvider) provider.resolveResourceProvider("Patient");
        bulkDataPatientProvider.setDao(jpaPatientProvider.getDao());
        bulkDataPatientProvider.setContext(jpaPatientProvider.getContext());

        try {
            unregister(jpaPatientProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(bulkDataPatientProvider, provider.getCollectionProviders());

        // Group processing - for bulk data export
        BulkDataGroupProvider bulkDataGroupProvider = new BulkDataGroupProvider(provider);
        GroupResourceProvider jpaGroupProvider = (GroupResourceProvider) provider.resolveResourceProvider("Group");
        bulkDataGroupProvider.setDao(jpaGroupProvider.getDao());
        bulkDataGroupProvider.setContext(jpaGroupProvider.getContext());

        try {
            unregister(jpaGroupProvider, provider.getCollectionProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(bulkDataGroupProvider, provider.getCollectionProviders());
    }

    private void register(IResourceProvider provider, Collection<IResourceProvider> providers) {
        providers.add(provider);
    }

    private void unregister(IResourceProvider provider, Collection<IResourceProvider> providers) {
        providers.remove(provider);
    }

    public IResourceProvider getProvider(String name) {

        for (IResourceProvider res : getResourceProviders()) {
            if (res.getResourceType().getSimpleName().equals(name)) {
                return res;
            }
        }

        throw new IllegalArgumentException("This should never happen!");
    }
}
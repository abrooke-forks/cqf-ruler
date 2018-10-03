package org.opencds.cqf.providers;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.rp.dstu3.LibraryResourceProvider;
import ca.uhn.fhir.jpa.rp.dstu3.MeasureResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.IncludeDef;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.opencds.cqf.config.STU3LibraryLoader;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.runtime.DateTime;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;
import org.opencds.cqf.evaluation.MeasureEvaluation;
import org.opencds.cqf.helpers.DateHelper;
import org.opencds.cqf.qdm.providers.QdmDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class FHIRMeasureResourceProvider extends MeasureResourceProvider {

    private JpaDataProvider provider;
    private STU3LibraryLoader libraryLoader;

    private Interval measurementPeriod;

    private static final Logger logger = LoggerFactory.getLogger(FHIRMeasureResourceProvider.class);

    public FHIRMeasureResourceProvider(JpaDataProvider dataProvider) {
        this.provider = dataProvider;
        this.libraryLoader =
                new STU3LibraryLoader(
                        (LibraryResourceProvider) provider.resolveResourceProvider("Library"),
                        new LibraryManager(new ModelManager()), new ModelManager()
                );
    }

    /*
    *
    * NOTE that the source, user, and pass parameters are not standard parameters for the FHIR $evaluate-measure operation
    *
    * */
    @Operation(name = "$evaluate-measure", idempotent = true)
    public MeasureReport evaluateMeasure(
            @IdParam IdType theId,
            @RequiredParam(name="periodStart") String periodStart,
            @RequiredParam(name="periodEnd") String periodEnd,
            @OptionalParam(name="measure") String measureRef,
            @OptionalParam(name="reportType") String reportType,
            @OptionalParam(name="patient") String patientRef,
            @OptionalParam(name="practitioner") String practitionerRef,
            @OptionalParam(name="lastReceivedOn") String lastReceivedOn,
            @OptionalParam(name="source") String source,
            @OptionalParam(name="user") String user,
            @OptionalParam(name="pass") String pass) throws InternalErrorException, FHIRException
    {
        Pair<Measure, Context> measureSetup = setup(measureRef == null ? "" : measureRef, theId, periodStart, periodEnd, source, user, pass);
        measureSetup.getRight().registerDataProvider("http://hl7.org/fhir", provider);


        // resolve report type
        MeasureEvaluation evaluator = new MeasureEvaluation(provider, measurementPeriod);
        if (reportType != null) {
            switch (reportType) {
                case "patient": return evaluator.evaluatePatientMeasure(measureSetup.getLeft(), measureSetup.getRight(), patientRef);
                case "patient-list": return  evaluator.evaluatePatientListMeasure(measureSetup.getLeft(), measureSetup.getRight(), practitionerRef);
                case "population": return evaluator.evaluatePopulationMeasure(measureSetup.getLeft(), measureSetup.getRight());
                default: throw new IllegalArgumentException("Invalid report type: " + reportType);
            }
        }

        // default report type is patient
        return evaluator.evaluatePatientMeasure(measureSetup.getLeft(), measureSetup.getRight(), patientRef);
    }

    @Operation(name = "$evaluate-qdm-measure", idempotent = true)
    public MeasureReport evaluateQDMMeasure(
            @IdParam IdType theId,
            @RequiredParam(name="periodStart") String periodStart,
            @RequiredParam(name="periodEnd") String periodEnd,
            @OptionalParam(name="measure") String measureRef,
            @OptionalParam(name="reportType") String reportType,
            @OptionalParam(name="patient") String patientRef,
            @OptionalParam(name="practitioner") String practitionerRef,
            @OptionalParam(name="lastReceivedOn") String lastReceivedOn,
            @OptionalParam(name="source") String source,
            @OptionalParam(name="user") String user,
            @OptionalParam(name="pass") String pass) throws InternalErrorException, FHIRException
    {
        QdmDataProvider dataProvider = new QdmDataProvider(provider.getCollectionProviders());
        dataProvider.setEndpoint("http://localhost:8080/cqf-ruler/baseDstu3");
        Pair<Measure, Context> measureSetup = setup(measureRef == null ? "" : measureRef, theId, periodStart, periodEnd, source, user, pass);
        measureSetup.getRight().registerDataProvider("urn:healthit-gov:qdm:v5_3", dataProvider);
        measureSetup.getRight().registerDataProvider("org.hl7.fhir.dstu3.model", dataProvider);


        // resolve report type
        MeasureEvaluation evaluator = new MeasureEvaluation(provider, measurementPeriod);
        if (reportType != null) {
            switch (reportType) {
                case "patient": return evaluator.evaluatePatientMeasure(measureSetup.getLeft(), measureSetup.getRight(), patientRef);
                case "patient-list": return  evaluator.evaluatePatientListMeasure(measureSetup.getLeft(), measureSetup.getRight(), practitionerRef);
                case "population": return evaluator.evaluatePopulationMeasure(measureSetup.getLeft(), measureSetup.getRight());
                default: throw new IllegalArgumentException("Invalid report type: " + reportType);
            }
        }

        // default report type is patient
        return evaluator.evaluatePatientMeasure(measureSetup.getLeft(), measureSetup.getRight(), patientRef);
    }

    @Operation(name = "$evaluate-measure-with-source", idempotent = true)
    public MeasureReport evaluateMeasure(
            @IdParam IdType theId,
            @OperationParam(name="sourceData", min = 1, max = 1, type = Bundle.class) Bundle sourceData,
            @OperationParam(name="periodStart", min = 1, max = 1) String periodStart,
            @OperationParam(name="periodEnd", min = 1, max = 1) String periodEnd)
    {
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("periodStart and periodEnd are required for measure evaluation");
        }
        Pair<Measure, Context> measureSetup = setup("", theId, periodStart, periodEnd, null, null, null);
        BundleDataProviderStu3 bundleProvider = new BundleDataProviderStu3(sourceData);
        bundleProvider.setTerminologyProvider(provider.getTerminologyProvider());
        measureSetup.getRight().registerDataProvider("http://hl7.org/fhir", bundleProvider);
        MeasureEvaluation evaluator = new MeasureEvaluation(bundleProvider, measurementPeriod);
        return evaluator.evaluatePatientMeasure(measureSetup.getLeft(), measureSetup.getRight(), "");
    }

    @Operation(name = "$care-gaps", idempotent = true)
    public Bundle careGapsReport(
            @RequiredParam(name="periodStart") String periodStart,
            @RequiredParam(name="periodEnd") String periodEnd,
            @RequiredParam(name="topic") String topic,
            @RequiredParam(name="patient") String patientRef
    ) {
        List<IBaseResource> measures = getDao().search(new SearchParameterMap().add("topic", new TokenParam().setModifier(TokenParamModifier.TEXT).setValue(topic))).getResources(0, 1000);
        Bundle careGapReport = new Bundle();
        careGapReport.setType(Bundle.BundleType.DOCUMENT);

        Composition composition = new Composition();
        // TODO - this is a placeholder code for now ... replace with preferred code once identified
        CodeableConcept typeCode = new CodeableConcept().addCoding(new Coding().setSystem("http://loinc.org").setCode("57024-2"));
        composition.setStatus(Composition.CompositionStatus.FINAL)
                .setType(typeCode)
                .setSubject(new Reference(patientRef.startsWith("Patient/") ? patientRef : "Patient/" + patientRef))
                .setTitle(topic + " Care Gap Report");

        List<MeasureReport> reports = new ArrayList<>();
        MeasureReport report = new MeasureReport();
        for (IBaseResource resource : measures) {
            Composition.SectionComponent section = new Composition.SectionComponent();

            Measure measure = (Measure) resource;
            section.addEntry(new Reference(measure.getIdElement().getResourceType() + "/" + measure.getIdElement().getIdPart()));
            if (measure.hasTitle()) {
                section.setTitle(measure.getTitle());
            }
            String improvementNotation = "increase"; // defaulting to "increase"
            if (measure.hasImprovementNotation()) {
                improvementNotation = measure.getImprovementNotation();
                section.setText(
                        new Narrative()
                                .setStatus(Narrative.NarrativeStatus.GENERATED)
                                .setDiv(new XhtmlNode().setValue(improvementNotation))
                );
            }

            Pair<Measure, Context> measureSetup = setup(measure, periodStart, periodEnd, null, null, null);
            MeasureEvaluation evaluator = new MeasureEvaluation(provider, measurementPeriod);
            // TODO - this is configured for patient-level evaluation only
            report = evaluator.evaluatePatientMeasure(measureSetup.getLeft(), measureSetup.getRight(), patientRef);

            if (report.hasGroup() && measure.hasScoring()) {
                int numerator = 0;
                int denominator = 0;
                for (MeasureReport.MeasureReportGroupComponent group : report.getGroup()) {
                    if (group.hasPopulation()) {
                        for (MeasureReport.MeasureReportGroupPopulationComponent population : group.getPopulation()) {
                            // TODO - currently configured for measures with only 1 numerator and 1 denominator
                            if (population.hasCode()) {
                                if (population.getCode().hasCoding()) {
                                    for (Coding coding : population.getCode().getCoding()) {
                                        if (coding.hasCode()) {
                                            if (coding.getCode().equals("numerator") && population.hasCount()) {
                                                numerator = population.getCount();
                                            }
                                            else if (coding.getCode().equals("denominator") && population.hasCount()) {
                                                denominator = population.getCount();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                double proportion = 0.0;
                if (measure.getScoring().hasCoding() && denominator != 0) {
                    for (Coding coding : measure.getScoring().getCoding()) {
                        if (coding.hasCode() && coding.getCode().equals("proportion")) {
                            proportion = numerator / denominator;
                        }
                    }
                }

                // TODO - this is super hacky ... change once improvementNotation is specified as a code
                if (improvementNotation.toLowerCase().contains("increase")) {
                    if (proportion < 1.0) {
                        composition.addSection(section);
                        reports.add(report);
                    }
                }
                else if (improvementNotation.toLowerCase().contains("decrease")) {
                    if (proportion > 0.0) {
                        composition.addSection(section);
                        reports.add(report);
                    }
                }

                // TODO - add other types of improvement notation cases
            }
        }

        careGapReport.addEntry(new Bundle.BundleEntryComponent().setResource(composition));

        for (MeasureReport rep : reports) {
            careGapReport.addEntry(new Bundle.BundleEntryComponent().setResource(rep));
        }

        return careGapReport;
    }

    private Pair<Measure, Context> setup(String measureRef, IdType theId, String periodStart,
                                         String periodEnd, String source, String user, String pass)
    {
        // fetch the measure
        Measure measure = this.getDao().read(measureRef == null || measureRef.isEmpty() ? theId : new IdType(measureRef));
        if (measure == null) {
            throw new IllegalArgumentException("Could not find Measure/" + theId);
        }

        return setup(measure, periodStart, periodEnd, source, user, pass);
    }

    private Pair<Measure, Context> setup(Measure measure, String periodStart,
                                         String periodEnd, String source, String user, String pass)
    {
        logger.info("Evaluating Measure/" + measure.getIdElement().getIdPart());

        // load libraries
        for (Reference ref : measure.getLibrary()) {
            // if library is contained in measure, load it into server
            if (ref.getReferenceElement().getIdPart().startsWith("#")) {
                for (Resource resource : measure.getContained()) {
                    if (resource instanceof org.hl7.fhir.dstu3.model.Library
                            && resource.getIdElement().getIdPart().equals(ref.getReferenceElement().getIdPart().substring(1)))
                    {
                        LibraryResourceProvider libraryResourceProvider = (LibraryResourceProvider) provider.resolveResourceProvider("Library");
                        libraryResourceProvider.getDao().update((org.hl7.fhir.dstu3.model.Library) resource);
                    }
                }
            }
            libraryLoader.load(
                    new VersionedIdentifier()
                            .withVersion(ref.getReferenceElement().getVersionIdPart())
                            .withId(ref.getReferenceElement().getIdPart())
            );
        }

        if (libraryLoader.getLibraries().isEmpty()) {
            throw new IllegalArgumentException(String.format("Could not load library source for libraries referenced in Measure/%s.", measure.getId()));
        }

        // resolve primary library
        Library library;
        if (libraryLoader.getLibraries().size() == 1) {
            library = libraryLoader.getLibraries().values().iterator().next();
        }
        else {
            library = resolvePrimaryLibrary(measure);
        }

        logger.info("Resolved primary library as Library/" + library.getLocalId());

        // resolve execution context
        Context context = new Context(library);
        context.registerLibraryLoader(libraryLoader);

        // resolve remote term svc if provided
        if (source != null) {
            logger.info("Remote terminology service provided");
            FhirTerminologyProvider terminologyProvider = user == null || pass == null
                    ? new FhirTerminologyProvider().setEndpoint(source, true)
                    : new FhirTerminologyProvider().withBasicAuth(user, pass).setEndpoint(source, true);
            provider.setTerminologyProvider(terminologyProvider);
        }

        context.registerDataProvider("http://hl7.org/fhir", provider);

        // resolve the measurement period
        measurementPeriod =
                new Interval(
                        DateHelper.resolveRequestDate(periodStart, true), true,
                        DateHelper.resolveRequestDate(periodEnd, false), true
                );

        logger.info("Measurement period defined as [" + measurementPeriod.getStart().toString() + ", " + measurementPeriod.getEnd().toString() + "]");

        context.setParameter(
                null, "Measurement Period",
                new Interval(
                        DateTime.fromJavaDate((Date) measurementPeriod.getStart()), true,
                        DateTime.fromJavaDate((Date) measurementPeriod.getEnd()), true
                )
        );

        return new ImmutablePair<>(measure, context);
    }

    private Library resolvePrimaryLibrary(Measure measure) {
        // default is the first library reference
        Library library = libraryLoader.getLibraries().get(measure.getLibraryFirstRep().getReferenceElement().getIdPart());

        // gather all the population criteria expressions
        List<String> criteriaExpressions = new ArrayList<>();
        for (Measure.MeasureGroupComponent grouping : measure.getGroup()) {
            for (Measure.MeasureGroupPopulationComponent population : grouping.getPopulation()) {
                criteriaExpressions.add(population.getCriteria());
            }
        }

        // check each library to see if it includes the expression namespace - return if true
        for (Library candidate : libraryLoader.getLibraries().values()) {
            for (String expression : criteriaExpressions) {
                String namespace = expression.split("\\.")[0];
                if (!namespace.equals(expression)) {
                    for (IncludeDef include : candidate.getIncludes().getDef()) {
                        if (include.getLocalIdentifier().equals(namespace)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return library;
    }

    // TODO - this needs a lot of work
    @Operation(name = "$data-requirements", idempotent = true)
    public org.hl7.fhir.dstu3.model.Library dataRequirements(
            @IdParam IdType theId,
            @RequiredParam(name="startPeriod") String startPeriod,
            @RequiredParam(name="endPeriod") String endPeriod)
            throws InternalErrorException, FHIRException
    {
        Measure measure = this.getDao().read(theId);

        // NOTE: This assumes there is only one library and it is the primary library for the measure.
        org.hl7.fhir.dstu3.model.Library libraryResource =
                (org.hl7.fhir.dstu3.model.Library) provider.resolveResourceProvider("Library")
                        .getDao()
                        .read(new IdType(measure.getLibraryFirstRep().getReference()));

        List<RelatedArtifact> dependencies = new ArrayList<>();
        for (RelatedArtifact dependency : libraryResource.getRelatedArtifact()) {
            if (dependency.getType().toCode().equals("depends-on")) {
                dependencies.add(dependency);
            }
        }

        List<Coding> typeCoding = new ArrayList<>();
        typeCoding.add(new Coding().setCode("module-definition"));
        org.hl7.fhir.dstu3.model.Library library =
                new org.hl7.fhir.dstu3.model.Library().setType(new CodeableConcept().setCoding(typeCoding));

        if (!dependencies.isEmpty()) {
            library.setRelatedArtifact(dependencies);
        }

        return library
                .setDataRequirement(libraryResource.getDataRequirement())
                .setParameter(libraryResource.getParameter());
    }

//    @Operation(name = "$submit-data", idempotent = true)
//    public Resource submitData(
//            RequestDetails details,
//            @IdParam IdType theId,
//            @OperationParam(name="measure-report", min = 1, max = 1, type = MeasureReport.class) MeasureReport report,
//            @OperationParam(name="resource", type = Bundle.class) Bundle model)
//    {
//        Measure measure = this.getDao().read(new IdType(theId.getIdPart()));
//
//        if (measure == null) {
//            throw new IllegalArgumentException(theId.getValue() + " does not exist");
//        }
//
//        // TODO - resource validation using $data-requirements operation
//        // TODO - profile validation
//
//        try {
//            provider.setEndpoint(details.getFhirServerBase());
//            return provider.getFhirClient().transaction().withBundle(createTransactionBundle(report, model)).execute();
//        } catch (Exception e) {
//            return new OperationOutcome().addIssue(
//                    new OperationOutcome.OperationOutcomeIssueComponent()
//                            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
//                            .setCode(OperationOutcome.IssueType.EXCEPTION)
//                            .setDetails(new CodeableConcept().setText(e.getMessage()))
//                            .setDiagnostics(ExceptionUtils.getStackTrace(e))
//            );
//        }
//    }

    @Operation(name = "$submit-data", idempotent = true)
    public Resource submitData(
            RequestDetails details,
            @IdParam IdType theId,
            @OperationParam(name="measure-report", min = 1, max = 1, type = MeasureReport.class) MeasureReport report,
            @OperationParam(name="resource") List<IAnyResource> resources)
    {
        Bundle transactionBundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);

        /*
            TODO - resource validation using $data-requirements operation
            (params are the provided id and the measurement period from the MeasureReport)

            TODO - profile validation ... not sure how that would work ...
            (get StructureDefinition from URL or must it be stored in Ruler?)
        */

        for (IAnyResource resource : resources) {
            Resource res = (Resource) resource;
            if (res instanceof Bundle) {
                for (Bundle.BundleEntryComponent entry : createTransactionBundle((Bundle) res).getEntry()) {
                    transactionBundle.addEntry(entry);
                }
            }
            else {
                // Build transaction bundle
                transactionBundle.addEntry(createTransactionEntry(res));
            }
        }

        // TODO - use FhirSystemDaoDstu3 to call transaction operation directly instead of building client
        provider.setEndpoint(details.getFhirServerBase());
        return provider.getFhirClient().transaction().withBundle(transactionBundle).execute();
    }

    private Bundle createTransactionBundle(Bundle bundle) {
        Bundle transactionBundle;
        if (bundle != null) {
            if (bundle.hasType() && bundle.getType() == Bundle.BundleType.TRANSACTION) {
                transactionBundle = bundle;
            }
            else {
                transactionBundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
                if (bundle.hasEntry()) {
                    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                        if (entry.hasResource()) {
                            transactionBundle.addEntry(createTransactionEntry(entry.getResource()));
                        }
                    }
                }
            }
        }
        else {
            transactionBundle = new Bundle().setType(Bundle.BundleType.TRANSACTION).setEntry(new ArrayList<>());
        }

        return transactionBundle;
    }

    private Bundle.BundleEntryComponent createTransactionEntry(Resource resource) {
        Bundle.BundleEntryComponent transactionEntry = new Bundle.BundleEntryComponent().setResource(resource);
        if (resource.hasId()) {
            transactionEntry.setRequest(
                    new Bundle.BundleEntryRequestComponent()
                            .setMethod(Bundle.HTTPVerb.PUT)
                            .setUrl(resource.getId())
            );
        }
        else {
            transactionEntry.setRequest(
                    new Bundle.BundleEntryRequestComponent()
                            .setMethod(Bundle.HTTPVerb.POST)
                            .setUrl(resource.fhirType())
            );
        }
        return transactionEntry;
    }
}

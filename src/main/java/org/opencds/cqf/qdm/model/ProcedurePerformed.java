package org.opencds.cqf.qdm.model;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Type;

import java.util.List;

@ResourceDef(name="ProcedurePerformed", profile="TODO")
public abstract class ProcedurePerformed extends QdmBaseType {

    @Child(name="authorDatetime", order=0)
    DateTimeType authorDatetime;
    public DateTimeType getAuthorDatetime() {
        return authorDatetime;
    }
    public ProcedurePerformed setAuthorDatetime(DateTimeType authorDatetime) {
        this.authorDatetime = authorDatetime;
        return this;
    }

	
    @Child(name="relevantPeriod", order=1)
    Period relevantPeriod;
    public Period getRelevantPeriod() {
        return relevantPeriod;
    }
    public ProcedurePerformed setRelevantPeriod(Period relevantPeriod) {
        this.relevantPeriod = relevantPeriod;
        return this;
    }	
	

    @Child(name="reason", order=2)
    Coding reason;
    public Coding getReason() {
        return reason;
    }
    public ProcedurePerformed setReason(Coding reason) {
        this.reason = reason;
        return this;
    }

	
    @Child(name="method", order=3)
    Coding method;
    public Coding getMethod() {
        return method;
    }
    public ProcedurePerformed setMethod(Coding method) {
        this.method = method;
        return this;
    }	


    @Child(name="result", order=4)
    Type result;
    public Type getResult() {
        return result;
    }
    public ProcedurePerformed setResult(Type result) {
        this.result = result;
        return this;
    }
	

    @Child(name="status", order=5)
    Coding status;
    public Coding getStatus() {
        return status;
    }
    public ProcedurePerformed setStatus(Coding status) {
        this.status = status;
        return this;
    }		

	
    @Child(name="anatomicalApproachSite", order=6)
    Coding anatomicalApproachSite;
    public Coding getAnatomicalApproachSite() {
        return anatomicalApproachSite;
    }
    public ProcedurePerformed setAnatomicalApproachSite(Coding anatomicalApproachSite) {
        this.anatomicalApproachSite = anatomicalApproachSite;
        return this;
    }

	
    @Child(name="anatomicalLocationSite", order=7)
    Coding anatomicalLocationSite;
    public Coding getAnatomicalLocationSite() {
        return anatomicalLocationSite;
    }
    public ProcedurePerformed setAnatomicalLocationSite(Coding anatomicalLocationSite) {
        this.anatomicalLocationSite = anatomicalLocationSite;
        return this;
    }


    @Child(name="ordinality", order=8)
    Coding ordinality;
    public Coding getOrdinality() {
        return ordinality;
    }
    public ProcedurePerformed setOrdinality(Coding ordinality) {
        this.ordinality = ordinality;
        return this;
    }	

    @Child(name="incisionDatetime", order=9)
    DateTimeType incisionDatetime;
    public DateTimeType getIncisionDatetime() {
        return incisionDatetime;
    }
    public ProcedurePerformed setIncisionDatetime(DateTimeType incisionDatetime) {
        this.incisionDatetime = incisionDatetime;
        return this;
    }	
	
	
    @Child(name="negationRationale", order=10)
    Coding negationRationale;
    public Coding getNegationRationale() {
        return negationRationale;
    }
    public ProcedurePerformed setNegationRationale(Coding negationRationale) {
        this.negationRationale = negationRationale;
        return this;
    }
	
	
    @Child(name="components", max=Child.MAX_UNLIMITED, order=11)
    List<Component> components;
    public List<Component> getComponents() {
        return components;
    }
    public ProcedurePerformed setComponents(List<Component> components) {
        this.components = components;
        return this;
    }
	


}

package org.opencds.cqf.qdm.model;

import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.hl7.fhir.dstu3.model.ResourceType;

@ResourceDef(name="NegativeEncounterPerformed", profile="TODO")
public class NegativeEncounterPerformed extends EncounterPerformed {
    @Override
    public NegativeEncounterPerformed copy() {
        NegativeEncounterPerformed retVal = new NegativeEncounterPerformed();
        super.copyValues(retVal);

        retVal.authorDatetime = authorDatetime;
        retVal.admissionSource = admissionSource;
        retVal.relevantPeriod = relevantPeriod;
        retVal.dischargeDisposition = dischargeDisposition;
        retVal.diagnoses = diagnoses;
        retVal.facilityLocation = facilityLocation;
        retVal.principalDiagnosis = principalDiagnosis;
        retVal.negationRationale = negationRationale;
        retVal.lengthOfStay = lengthOfStay;

        return retVal;
    }

    @Override
    public ResourceType getResourceType() {
        return null;
    }

    @Override
    public String getResourceName() {
        return "NegativeEncounterPerformed";
    }
}

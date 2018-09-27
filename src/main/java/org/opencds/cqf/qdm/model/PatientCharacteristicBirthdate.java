package org.opencds.cqf.qdm.model;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.ResourceType;


@ResourceDef(name="PatientCharacteristicBirthdate", profile="TODO")
public class PatientCharacteristicBirthdate extends QdmBaseType {

    @Child(name="birthDatetime", order=0)
    private DateTimeType birthDatetime;
    public DateTimeType getBirthDatetime() {
        return birthDatetime;
    }
    public PatientCharacteristicBirthdate setBirthDatetime(DateTimeType birthDatetime) {
        this.birthDatetime = birthDatetime;
        return this;
    }	

    @Override
    public PatientCharacteristicBirthdate copy() {
        PatientCharacteristicBirthdate retVal = new PatientCharacteristicBirthdate();
        super.copyValues(retVal);
        retVal.birthDatetime = birthDatetime;
        return retVal;
    }

    @Override
    public ResourceType getResourceType() {
        return null;
    }

    @Override
    public String getResourceName() {
        return "PatientCharacteristicBirthdate";
    }

}

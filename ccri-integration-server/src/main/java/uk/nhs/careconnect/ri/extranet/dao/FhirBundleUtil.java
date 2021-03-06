package uk.nhs.careconnect.ri.extranet.dao;

import org.hl7.fhir.dstu3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class FhirBundleUtil {

    // THIS IS A LIBRARY CLASS !!!BUT!!! IS CURRENTLY IN SEVERAL MODULES

    // BE CAREFUL AND UPDATE ALL VERSIONS.
    //
    // This will be moved to a central library when development is at that stage

    private Bundle fhirDocument = null;

    private Patient patient = null;

    Map<String,String> referenceMap = new HashMap<>();

    final String uuidtag = "urn:uuid:";

    private static final Logger log = LoggerFactory.getLogger(FhirBundleUtil.class);

    public FhirBundleUtil(Bundle.BundleType value) {
        fhirDocument = new Bundle()
                .setType(value);
        fhirDocument.getIdentifier().setValue(UUID.randomUUID().toString()).setSystem("https://tools.ietf.org/html/rfc4122");
    }

    public Bundle getFhirDocument() {
        return fhirDocument;
    }

    public void setFhirDocument(Bundle fhirDocument) {
        this.fhirDocument = fhirDocument;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public void processReferences() {

        for (Bundle.BundleEntryComponent entry : fhirDocument.getEntry()) {

            if (entry.getResource() instanceof AllergyIntolerance) {
                AllergyIntolerance allergyIntolerance = (AllergyIntolerance) entry.getResource();
                allergyIntolerance.setPatient(getUUIDReference(allergyIntolerance.getPatient()));
            }

            if (entry.getResource() instanceof CarePlan) {
                CarePlan carePlan = (CarePlan) entry.getResource();
                carePlan.setSubject(new Reference(uuidtag+patient.getId()));
                if (carePlan.hasContext()) {
                    carePlan.setContext(getUUIDReference(carePlan.getContext()));
                }
                for (Reference reference : carePlan.getCareTeam()) {
                    reference.setReference(getUUIDReference(reference).getReference());
                }
               for (Reference reference : carePlan.getSupportingInfo()) {
                   reference.setReference(getUUIDReference(reference).getReference());
               }
            }

            if (entry.getResource() instanceof CareTeam) {
                CareTeam careTeam = (CareTeam) entry.getResource();
                careTeam.setSubject(new Reference(uuidtag+patient.getId()));
                if (careTeam.hasContext()) {
                    careTeam.setContext(getUUIDReference(careTeam.getContext()));
                }
                for (CareTeam.CareTeamParticipantComponent participant : careTeam.getParticipant()) {
                    if (participant.hasMember()) {
                        participant.setMember(getUUIDReference(participant.getMember()));

                    }
                    if (participant.hasOnBehalfOf()) {
                        participant.setOnBehalfOf(getUUIDReference(participant.getOnBehalfOf()));

                    }
                }
                for (Reference reference : careTeam.getManagingOrganization()) {
                    reference.setReference(getUUIDReference(reference).getReference());
                }
            }

            if (entry.getResource() instanceof ClinicalImpression) {
                ClinicalImpression clinicalImpression = (ClinicalImpression) entry.getResource();
                clinicalImpression.setSubject(getUUIDReference(clinicalImpression.getSubject()));
                if (clinicalImpression.hasAssessor()) {
                    clinicalImpression.setAssessor(getUUIDReference(clinicalImpression.getAssessor()));
                }

            }

            if (entry.getResource() instanceof Condition) {
                Condition condition = (Condition) entry.getResource();
                condition.setSubject(getUUIDReference(condition.getSubject()));
                if (condition.hasAsserter()) {
                    condition.setAsserter(getUUIDReference(condition.getAsserter()));
                }
                if (condition.hasContext()) {
                    condition.setContext(getUUIDReference(condition.getContext()));
                }
            }
            if (entry.getResource() instanceof Consent) {
                Consent consent = (Consent) entry.getResource();
                consent.setPatient(getUUIDReference(consent.getPatient()));
                for (Reference reference : consent.getOrganization()) {
                    reference.setReference(getUUIDReference(reference).getReference());
                }
                for (Reference reference : consent.getConsentingParty()) {
                    reference.setReference(getUUIDReference(reference).getReference());
                }
                for (Consent.ConsentActorComponent consentActorComponent : consent.getActor()) {
                    if (consentActorComponent.hasReference()) {
                        consentActorComponent.setReference(getUUIDReference(consentActorComponent.getReference()));
                    }
                }
            }

            if (entry.getResource() instanceof Composition) {
                Composition composition = (Composition) entry.getResource();

                if (composition.hasSubject()) composition.setSubject(getUUIDReference(composition.getSubject()));
                for (Composition.CompositionAttesterComponent attester : composition.getAttester()) {
                    if (attester.hasParty()) {
                        attester.setParty(getUUIDReference(attester.getParty()));
                    }
                }
                for (Reference reference : composition.getAuthor()) {
                    reference.setReference(getUUIDReference(reference).getReference());
                }
            }
            if (entry.getResource() instanceof DocumentReference) {
                DocumentReference documentReference = (DocumentReference) entry.getResource();
                if (documentReference.hasSubject()) {
                    documentReference.setSubject(getUUIDReference(documentReference.getSubject()));
                }
                if (documentReference.hasContent()) {
                    for (DocumentReference.DocumentReferenceContentComponent content : documentReference.getContent())
                    if (content.hasAttachment()) {
                        log.debug("Attachment url = "+content.getAttachment().getUrl());
                        content.getAttachment().setUrl(uuidtag+getNewReferenceUri(content.getAttachment().getUrl()));
                    }
                }
                if (documentReference.hasCustodian()) {
                   // log.info("Bundle Custodian Ref = "+documentReference.getCustodian().getReference());
                    documentReference.setCustodian(getUUIDReference(documentReference.getCustodian()));
                }
                for (Reference reference : documentReference.getAuthor()) {
                    reference.setReference(getUUIDReference(reference).getReference());
                }
            }
            if (entry.getResource() instanceof Device) {
                Device device = (Device) entry.getResource();
                if (device.hasOwner()) {
                    device.setOwner(getUUIDReference(device.getOwner()));
                }
            }

            if (entry.getResource() instanceof Encounter) {
                Encounter encounter = (Encounter) entry.getResource();
                encounter.setSubject(new Reference(uuidtag+patient.getId()));
                if (encounter.hasServiceProvider() && encounter.getServiceProvider().getReference()!=null) {
                    encounter.setServiceProvider(getUUIDReference(encounter.getServiceProvider()));
                } else {
                    encounter.setServiceProvider(null);
                }
                List<Reference> newReferences = new ArrayList<>();
                for (Reference reference : encounter.getEpisodeOfCare()) {
                    Reference newReference =getUUIDReference(reference);
                    if (newReference != null) { newReferences.add(newReference); }
                }
                encounter.setEpisodeOfCare(newReferences);

                for (Encounter.EncounterLocationComponent locationComponent : encounter.getLocation()) {
                    locationComponent.setLocation(getUUIDReference(locationComponent.getLocation()));
                }
                for (Encounter.EncounterParticipantComponent participantComponent : encounter.getParticipant()) {
                    if (participantComponent.hasIndividual()) participantComponent.setIndividual(getUUIDReference(participantComponent.getIndividual()));
                }
            }

            if (entry.getResource() instanceof ListResource) {
                ListResource list = (ListResource) entry.getResource();
                list.setSubject(new Reference(uuidtag+patient.getId()));

                for (ListResource.ListEntryComponent listEntry : list.getEntry()) {
                    listEntry.setItem(getUUIDReference(listEntry.getItem()));
                }

                if (list.hasSource()) {
                    list.setSource(getUUIDReference(list.getSource()));
                }
                if (list.hasEncounter()) {
                    list.setEncounter(getUUIDReference(list.getEncounter()));
                }

            }
            if (entry.getResource() instanceof Location) {
                Location location = (Location) entry.getResource();

                if (location.hasManagingOrganization()) {
                    location.setManagingOrganization(getUUIDReference(location.getManagingOrganization()));
                }
                if (location.hasPartOf()) {
                    location.setPartOf(getUUIDReference(location.getPartOf()));
                }

            }

            if (entry.getResource() instanceof Observation) {
                Observation observation = (Observation) entry.getResource();
                observation.setSubject(new Reference(uuidtag+patient.getId()));
                if (observation.hasContext()) {
                    observation.setContext(getUUIDReference(observation.getContext()));
                }

            }

            if (entry.getResource() instanceof MedicationRequest) {
                MedicationRequest medicationRequest = (MedicationRequest) entry.getResource();
                if (medicationRequest.hasContext()) {
                    medicationRequest.setContext(getUUIDReference(medicationRequest.getContext()));
                }
                medicationRequest.setSubject(new Reference(uuidtag+patient.getId()));
                if (medicationRequest.hasRecorder()) medicationRequest.setRecorder(getUUIDReference(medicationRequest.getRecorder()));
                try {
                    if (medicationRequest.hasMedicationReference())
                    medicationRequest.setMedication(getUUIDReference(medicationRequest.getMedicationReference()));
                } catch (Exception ex) {};

            }

            if (entry.getResource() instanceof MedicationStatement) {
                MedicationStatement medicationStatement = (MedicationStatement) entry.getResource();
                medicationStatement.setSubject(new Reference(uuidtag+patient.getId()));
                if (medicationStatement.hasContext()) {
                    medicationStatement.setContext(getUUIDReference(medicationStatement.getContext()));
                }
            }

            if (entry.getResource() instanceof Organization) {
                Organization organization = (Organization) entry.getResource();
                if (organization.hasPartOf()) {
                    if (getNewReferenceUri(organization.getPartOf().getReference())!=null) {
                        organization.setPartOf(getUUIDReference(organization.getPartOf()));
                    } else {
                        organization.setPartOf(null);
                    }
                }
            }

            if (entry.getResource() instanceof Patient) {
                Patient patient = (Patient) entry.getResource();

                if (patient.hasManagingOrganization()) {
                    patient.setManagingOrganization(new Reference(uuidtag + getNewReferenceUri(patient.getManagingOrganization().getReference())));
                }
                for (Reference reference : patient.getGeneralPractitioner()) {
                    patient.getGeneralPractitioner().get(0).setReference(uuidtag + getNewReferenceUri(reference.getReference()));
                }
            }

            if (entry.getResource() instanceof Practitioner) {
                Practitioner practitioner = (Practitioner) entry.getResource();
            }

            if (entry.getResource() instanceof Procedure) {
                Procedure procedure = (Procedure) entry.getResource();
                procedure.setSubject(new Reference(uuidtag+patient.getId()));
                if (procedure.hasContext()) {
                    procedure.setContext(getUUIDReference(procedure.getContext()));
                }
            }
            if (entry.getResource() instanceof RiskAssessment) {
                RiskAssessment riskAssessment = (RiskAssessment) entry.getResource();
                riskAssessment.setSubject(new Reference(uuidtag+patient.getId()));
                if (riskAssessment.hasContext()) {
                    riskAssessment.setContext(getUUIDReference(riskAssessment.getContext()));
                }
                if (riskAssessment.hasCondition()) {
                    riskAssessment.setCondition(getUUIDReference(riskAssessment.getCondition()));
                }
            }
            if (entry.getResource() instanceof QuestionnaireResponse) {
                QuestionnaireResponse form = (QuestionnaireResponse) entry.getResource();
                form.setSubject(new Reference(uuidtag+patient.getId()));
                if (form.hasContext()) {
                    form.setContext(getUUIDReference(form.getContext()));
                }
                if (form.hasSource()) {
                    form.setSource(getUUIDReference(form.getSource()));
                }
                if (form.hasAuthor()) {
                    form.setAuthor(getUUIDReference(form.getAuthor()));
                }
            }
        }
    }

    public void processBundleResources(Bundle bundle) {
        Practitioner gp = null;
        Organization practice = null;
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            resource.setId(getNewId(resource));
            fhirDocument.addEntry().setResource(entry.getResource()).setFullUrl(uuidtag + resource.getId());
            if (entry.getResource() instanceof Patient) {
                patient = (Patient) entry.getResource();
            }
        }
    }

    private Reference getUUIDReference(Reference reference) {
        if (referenceMap.get(reference.getReference()) == null) {
            log.error("Missing reference "+reference.getReference());
        }
        if (reference.getReference().equals(getNewReferenceUri(reference.getReference()))) {
            return reference;
        } else {
            String UUIDReference = getNewReferenceUri(reference.getReference());
            if (UUIDReference!=null) {
                return new Reference(uuidtag + UUIDReference);
            } else { return null; }
        }
    }
    private String getNewReferenceUri(Resource resource) {
        return getNewReferenceUri(resource.getResourceType().toString()+"/"+resource.getId());
    }

    public String getNewId(Resource resource) {
        String reference = resource.getId();
        String newReference = null;
        if (reference!=null) {
            newReference = referenceMap.get(reference);
            if (newReference != null) return newReference;
        }
        newReference = UUID.randomUUID().toString();
        String simpleName = resource.getClass().getSimpleName();
        if (simpleName.equals("ListResource")) simpleName="List";
        if (reference == null) {
            reference = newReference;
            referenceMap.put(simpleName+"/"+reference,newReference);
        } else {

            log.info(simpleName+"/"+resource.getIdElement().getIdPart()+" [-] "+newReference);
            referenceMap.put(simpleName+"/"+resource.getIdElement().getIdPart(),newReference);
        }
        log.info(reference +" [-] "+newReference);
        referenceMap.put(reference,newReference);
        referenceMap.put(newReference,newReference); // Add in self
        referenceMap.put(uuidtag + newReference,newReference); // Add in self

        return newReference;
    }
    private String getNewReferenceUri(String reference) {
        if (reference.contains(uuidtag)) {
            return reference.replace(uuidtag,"");
        }
        String newReference = referenceMap.get(reference);
        if (newReference != null ) return newReference;

        log.info("Missing newReference for "+reference);

        return newReference;
    }

}

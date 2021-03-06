package uk.nhs.careconnect.ri.extranet.providers;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.annotation.Initialize;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.IServerConformanceProvider;
import ca.uhn.fhir.rest.server.ResourceBinding;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.RestulfulServerConfiguration;
import ca.uhn.fhir.rest.server.method.*;
import ca.uhn.fhir.rest.server.method.SearchParameter;
import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;

import org.hl7.fhir.instance.model.Conformance;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;


import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

import static org.apache.http.util.TextUtils.isBlank;

@Component
public class ConformanceExtranetProvider implements IServerConformanceProvider<CapabilityStatement> {

    /*

    This is a stripped down version of package org.hl7.fhir.dstu3.hapi.rest.server
    public class ServerCapabilityStatementProvider

    We remove StructureDefinition and OperationOutcome
    Also add in the Care Connect profiles instead of the baseProfiles.

     */


    private CamelContext context;

    public ConformanceExtranetProvider(ApplicationContext applicationContext) {
        this.context = applicationContext.getBean(CamelContext.class);
    }

    public ConformanceExtranetProvider(String oauth2authorize
            , String oauth2token
            , String oauth2register
          ) {
        log.trace("oauth2authorize = "+oauth2authorize);
        log.trace("oauth2register = "+oauth2register);
        log.trace("oauth2token = "+oauth2token);
        this.oauth2authorize = oauth2authorize;
        this.oauth2register = oauth2register;
        this.oauth2token = oauth2token;
    }

    public ConformanceExtranetProvider() {
        this.oauth2authorize = null;
        this.oauth2register = null;
        this.oauth2token = null;

    }



    private RestulfulServerConfiguration serverConfiguration;

    private volatile CapabilityStatement capabilityStatement;

    private volatile CapabilityStatement serverCapabilityStatement;

    private static final Logger log = LoggerFactory.getLogger(ConformanceExtranetProvider.class);

    private IdentityHashMap<OperationMethodBinding, String> myOperationBindingToName;
    private HashMap<String, List<OperationMethodBinding>> myOperationNameToBindings;

    private String oauth2authorize;

    private String oauth2token;

    private String oauth2register;

    @Value("${fhir.resource.serverName}")
    private String serverName;

    @Value("${fhir.resource.serverVersion}")
    private String serverVersion;

    @Override
    @Metadata
    public CapabilityStatement getServerConformance(HttpServletRequest theRequest) {

        // TODO maybe remove this or put on a refresh timer
        /*
        if (capabilityStatement != null ) {
            return capabilityStatement;
        }
        */

        CapabilityStatement retVal = new CapabilityStatement();


        if (context != null) {
            ProducerTemplate template = context.createProducerTemplate();

            try {
                InputStream inputStream = (InputStream) template.sendBody("direct:FHIRCapabilityStatement",
                        ExchangePattern.InOut, theRequest);

                Reader reader = new InputStreamReader(inputStream);
                FhirContext ctx = serverConfiguration.getFhirContext();
                serverCapabilityStatement = ctx
                        .newJsonParser()
                        .parseResource(CapabilityStatement.class, reader);
                log.debug("ServerCapabilityStatement="+ctx.newJsonParser().encodeResourceToString(serverCapabilityStatement));
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }

        capabilityStatement = retVal;

        retVal.setPublisher("NHS Digital");
        retVal.setDateElement(conformanceDate());
        retVal.setFhirVersion(FhirVersionEnum.DSTU3.getFhirVersionString());
        retVal.setAcceptUnknown(CapabilityStatement.UnknownContentCode.EXTENSIONS); // TODO: make this configurable - this is a fairly big
        // effort since the parser
        // needs to be modified to actually allow it

        retVal.getImplementation().setDescription(serverConfiguration.getImplementationDescription());
        retVal.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);

        // TODO KGM move to config
        retVal.getSoftware().setName("Care Connect RI FHIR Server");
        retVal.getSoftware().setVersion("3.5.1-SNAPSHOT");
        retVal.addFormat(Constants.CT_FHIR_XML_NEW);
        retVal.addFormat(Constants.CT_FHIR_JSON_NEW);
        retVal.setStatus(Enumerations.PublicationStatus.ACTIVE);

        CapabilityStatement.CapabilityStatementRestComponent rest = retVal.addRest();

        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

        if (oauth2token != null && oauth2register !=null && oauth2authorize != null) {
            rest.getSecurity()
                    .addService().addCoding()
                    .setSystem("http://hl7.org/fhir/restful-security-service")
                    .setDisplay("SMART-on-FHIR")
                    .setSystem("SMART-on-FHIR");
            Extension securityExtension = rest.getSecurity().addExtension()
                    .setUrl("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");

            securityExtension.addExtension()
                    .setUrl("authorize")
                    .setValue(new UriType(oauth2authorize));

            securityExtension.addExtension()
                    .setUrl("register")
                    .setValue(new UriType(oauth2register));

            securityExtension.addExtension()
                    .setUrl("token")
                    .setValue(new UriType(oauth2token));
        }

        Set<Conformance.SystemRestfulInteraction> systemOps = new HashSet<Conformance.SystemRestfulInteraction>();
        Set<String> operationNames = new HashSet<String>();

        Map<String, List<BaseMethodBinding<?>>> resourceToMethods = collectMethodBindings();
        for (Map.Entry<String, List<BaseMethodBinding<?>>> nextEntry : resourceToMethods.entrySet()) {

            if (nextEntry.getKey().isEmpty() == false) {
                Set<CapabilityStatement.TypeRestfulInteraction> resourceOps = new HashSet<CapabilityStatement.TypeRestfulInteraction>();
                CapabilityStatement.CapabilityStatementRestResourceComponent resource = rest.addResource();
                String resourceName = nextEntry.getKey();
                RuntimeResourceDefinition def = serverConfiguration.getFhirContext().getResourceDefinition(resourceName);
                resource.getTypeElement().setValue(def.getName());
                ServletContext servletContext = (ServletContext) (theRequest == null ? null : theRequest.getAttribute(RestfulServer.SERVLET_CONTEXT_ATTRIBUTE));
                String serverBase = serverConfiguration.getServerAddressStrategy().determineServerBase(servletContext, theRequest);
                switch (resourceName) {
                    case "Patient":
                        resource.getProfile().setReference(CareConnectProfile.Patient_1);
                        break;
                    case "Practitioner":
                        resource.getProfile().setReference(CareConnectProfile.Practitioner_1);
                        break;
                    case "PractitionerRole":
                        resource.getProfile().setReference(CareConnectProfile.PractitionerRole_1);
                        break;
                    case "Organization":
                        resource.getProfile().setReference(CareConnectProfile.Organization_1);
                        break;
                    case "Location":
                        resource.getProfile().setReference(CareConnectProfile.Location_1);
                        break;
                    case "Observation":
                        resource.getProfile().setReference(CareConnectProfile.Observation_1);
                        break;
                    case "Encounter":
                        resource.getProfile().setReference(CareConnectProfile.Encounter_1);
                        break;
                    case "Condition":
                        resource.getProfile().setReference(CareConnectProfile.Condition_1);
                        break;
                    case "Procedure":
                        resource.getProfile().setReference(CareConnectProfile.Procedure_1);
                        break;
                    case "Immunization":
                        resource.getProfile().setReference(CareConnectProfile.Immunization_1);
                        break;
                    case "MedicationRequest":
                        resource.getProfile().setReference(CareConnectProfile.MedicationRequest_1);
                        break;
                    case "MedicationStatement":
                        resource.getProfile().setReference(CareConnectProfile.MedicationStatement_1);
                        break;
                    case "AllergyIntolerance":
                        resource.getProfile().setReference(CareConnectProfile.AllergyIntolerance_1);
                        break;
                    case "Medication":
                        resource.getProfile().setReference(CareConnectProfile.Medication_1);
                        break;
                    default:
                        resource.getProfile().setReference((def.getResourceProfile(serverBase)));
                }


                TreeSet<String> includes = new TreeSet<String>();


                for (BaseMethodBinding<?> nextMethodBinding : nextEntry.getValue()) {
                    if (nextMethodBinding.getRestOperationType() != null) {
                        String resOpCode = nextMethodBinding.getRestOperationType().getCode();
                        if (resOpCode != null) {
                            CapabilityStatement.TypeRestfulInteraction resOp;
                            try {
                                resOp = CapabilityStatement.TypeRestfulInteraction.fromCode(resOpCode);
                            } catch (Exception e) {
                                resOp = null;
                            }
                            if (resOp != null) {
                                if (resourceOps.contains(resOp) == false) {
                                    resourceOps.add(resOp);
                                    resource.addInteraction().setCode(resOp);
                                }
                                if ("vread".equals(resOpCode)) {
                                    // vread implies read
                                    resOp = CapabilityStatement.TypeRestfulInteraction.READ;
                                    if (resourceOps.contains(resOp) == false) {
                                        resourceOps.add(resOp);
                                        resource.addInteraction().setCode(resOp);
                                    }
                                }

                                if (nextMethodBinding.isSupportsConditional()) {
                                    switch (resOp) {
                                        case CREATE:
                                            resource.setConditionalCreate(true);
                                            break;
                                        case DELETE:
                                            if (nextMethodBinding.isSupportsConditionalMultiple()) {
                                                resource.setConditionalDelete(CapabilityStatement.ConditionalDeleteStatus.MULTIPLE);
                                            } else {
                                                resource.setConditionalDelete(CapabilityStatement.ConditionalDeleteStatus.SINGLE);
                                            }
                                            break;
                                        case UPDATE:
                                            resource.setConditionalUpdate(true);
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            }
                        }
                        //checkBindingForSystemOps(rest, systemOps, nextMethodBinding);

                        if (nextMethodBinding instanceof SearchMethodBinding) {
                            handleSearchMethodBinding(rest, resource, resourceName, def, includes, (SearchMethodBinding) nextMethodBinding);
                        } else if (nextMethodBinding instanceof DynamicSearchMethodBinding) {
                            handleDynamicSearchMethodBinding(resource, def, includes, (DynamicSearchMethodBinding) nextMethodBinding);
                        } else if (nextMethodBinding instanceof OperationMethodBinding) {
                            OperationMethodBinding methodBinding = (OperationMethodBinding) nextMethodBinding;
                            String opName = myOperationBindingToName.get(methodBinding);
                            if (operationNames.add(opName)) {
                                // Only add each operation (by name) once
                                rest.addOperation().setName(methodBinding.getName().substring(1)).setDefinition(new Reference("OperationDefinition/" + opName));
                            }
                        }
                    }
                }
                for (String nextInclude : includes) {
                    resource.addSearchInclude(nextInclude);
                }

            }
        }
        // Add resource counts from server
        if (serverCapabilityStatement != null) {
            log.trace("Server CS not null");
            for (CapabilityStatement.CapabilityStatementRestComponent nextRest : retVal.getRest()) {
                for (CapabilityStatement.CapabilityStatementRestResourceComponent restResourceComponent : nextRest.getResource()) {
                    log.trace("restResourceComponent.getType - " + restResourceComponent.getType());
                    for (CapabilityStatement.CapabilityStatementRestComponent nextRestServer : serverCapabilityStatement.getRest()) {
                        for (CapabilityStatement.CapabilityStatementRestResourceComponent restResourceComponentServer : nextRestServer.getResource()) {
                            if (restResourceComponent.getType().equals(restResourceComponentServer.getType())
                                    && restResourceComponentServer.getExtensionFirstRep() !=null)

                                    restResourceComponent.addExtension()
                                            .setUrl(restResourceComponentServer.getExtensionFirstRep().getUrl())
                                            .setValue(restResourceComponentServer.getExtensionFirstRep().getValue());
                        }

                    }
                    // No extensions found, so add in a zero result to prevent hapiUI display error
                    if (restResourceComponent.getExtension().size() == 0) {
                        restResourceComponent.addExtension()
                                .setUrl("http://hl7api.sourceforge.net/hapi-fhir/res/extdefs.html#resourceCount")
                                .setValue(new DecimalType(0));
                    }
                }
            }

        } else {
            log.trace("Server CS IS NULL");
        }
        return retVal;
    }

    @Override
    public void setRestfulServer(RestfulServer theRestfulServer) {
        serverConfiguration = theRestfulServer.createConfiguration();
    }

    private DateTimeType conformanceDate() {
        String buildDate = serverConfiguration.getConformanceDate();
        if (buildDate != null) {
            try {
                return new DateTimeType(buildDate);
            } catch (DataFormatException e) {
                // fall through
            }
        }
        return DateTimeType.now();
    }

    private Map<String, List<BaseMethodBinding<?>>> collectMethodBindings() {
        Map<String, List<BaseMethodBinding<?>>> resourceToMethods = new TreeMap<>();
        for (ResourceBinding next : serverConfiguration.getResourceBindings()) {
            String resourceName = next.getResourceName();
            log.trace("CapabilityStatement=" + resourceName);
            switch (resourceName) {
               // Add supported resources here. This is suppressing StructureDefinition which is part of equivalent hapi class

                case "Patient" :
                case "Practitioner":
                case "PractitionerRole":
                case "Organization":
                case "Location":
                case "Observation":
                case "Encounter":
                case "EpisodeOfCare":
                case "Condition":
                case "Procedure":
                case "Immunization":
                case "MedicationRequest":
                case "MedicationStatement":
                case "AllergyIntolerance":
                case "Bundle":
                case "Composition":
                case "DocumentReference":
                case "Medication":


                for (BaseMethodBinding<?> nextMethodBinding : next.getMethodBindings()) {
                    if (resourceToMethods.containsKey(resourceName) == false) {
                        resourceToMethods.put(resourceName, new ArrayList<>());
                    }
                    resourceToMethods.get(resourceName).add(nextMethodBinding);

                }
            }
        }

        for (BaseMethodBinding<?> nextMethodBinding : serverConfiguration.getServerBindings()) {
            String resourceName = "";
            if (resourceToMethods.containsKey(resourceName) == false) {
                resourceToMethods.put(resourceName, new ArrayList<BaseMethodBinding<?>>());
            }
            resourceToMethods.get(resourceName).add(nextMethodBinding);
        }

        return resourceToMethods;
    }


    private void sortSearchParameters(List<SearchParameter> searchParameters) {
        Collections.sort(searchParameters, new Comparator<SearchParameter>() {
            @Override
            public int compare(SearchParameter theO1, SearchParameter theO2) {
                if (theO1.isRequired() == theO2.isRequired()) {
                    return theO1.getName().compareTo(theO2.getName());
                }
                if (theO1.isRequired()) {
                    return -1;
                }
                return 1;
            }
        });
    }

    private void handleDynamicSearchMethodBinding(CapabilityStatement.CapabilityStatementRestResourceComponent resource, RuntimeResourceDefinition def, TreeSet<String> includes, DynamicSearchMethodBinding searchMethodBinding) {
        includes.addAll(searchMethodBinding.getIncludes());

        List<RuntimeSearchParam> searchParameters = new ArrayList<RuntimeSearchParam>();
        searchParameters.addAll(searchMethodBinding.getSearchParams());
        sortRuntimeSearchParameters(searchParameters);

        if (!searchParameters.isEmpty()) {

            for (RuntimeSearchParam nextParameter : searchParameters) {

                String nextParamName = nextParameter.getName();

                // String chain = null;
                String nextParamUnchainedName = nextParamName;
                if (nextParamName.contains(".")) {
                    // chain = nextParamName.substring(nextParamName.indexOf('.') + 1);
                    nextParamUnchainedName = nextParamName.substring(0, nextParamName.indexOf('.'));
                }

                String nextParamDescription = nextParameter.getDescription();

                /*
                 * If the parameter has no description, default to the one from the resource
                 */
                if (StringUtils.isBlank(nextParamDescription)) {
                    RuntimeSearchParam paramDef = def.getSearchParam(nextParamUnchainedName);
                    if (paramDef != null) {
                        nextParamDescription = paramDef.getDescription();
                    }
                }

                CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent param = resource.addSearchParam();

                param.setName(nextParamName);
                // if (StringUtils.isNotBlank(chain)) {
                // param.addChain(chain);
                // }
                param.setDocumentation(nextParamDescription);
                // param.setType(nextParameter.getParamType());
            }
        }
    }


    private void handleSearchMethodBinding(CapabilityStatement.CapabilityStatementRestComponent rest, CapabilityStatement.CapabilityStatementRestResourceComponent resource, String resourceName, RuntimeResourceDefinition def, TreeSet<String> includes,
                                           SearchMethodBinding searchMethodBinding) {
        includes.addAll(searchMethodBinding.getIncludes());

        List<IParameter> params = searchMethodBinding.getParameters();
        List<SearchParameter> searchParameters = new ArrayList<SearchParameter>();
        for (IParameter nextParameter : params) {
            if ((nextParameter instanceof SearchParameter)) {
                searchParameters.add((SearchParameter) nextParameter);
            }
        }
        sortSearchParameters(searchParameters);
        if (!searchParameters.isEmpty()) {
            // boolean allOptional = searchParameters.get(0).isRequired() == false;
            //
            // OperationDefinition query = null;
            // if (!allOptional) {
            // RestOperation operation = rest.addOperation();
            // query = new OperationDefinition();
            // operation.setDefinition(new ResourceReferenceDt(query));
            // query.getDescriptionElement().setValue(searchMethodBinding.getDescription());
            // query.addUndeclaredExtension(false, ExtensionConstants.QUERY_RETURN_TYPE, new CodeDt(resourceName));
            // for (String nextInclude : searchMethodBinding.getIncludes()) {
            // query.addUndeclaredExtension(false, ExtensionConstants.QUERY_ALLOWED_INCLUDE, new StringDt(nextInclude));
            // }
            // }

            for (SearchParameter nextParameter : searchParameters) {

                String nextParamName = nextParameter.getName();

                String chain = null;
                String nextParamUnchainedName = nextParamName;
                if (nextParamName.contains(".")) {
                    chain = nextParamName.substring(nextParamName.indexOf('.') + 1);
                    nextParamUnchainedName = nextParamName.substring(0, nextParamName.indexOf('.'));
                }

                String nextParamDescription = nextParameter.getDescription();

				/*
				 * If the parameter has no description, default to the one from the resource
				 */
                if (isBlank(nextParamDescription)) {
                    RuntimeSearchParam paramDef = def.getSearchParam(nextParamUnchainedName);
                    if (paramDef != null) {
                        nextParamDescription = paramDef.getDescription();
                    }
                }

                CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent param = resource.addSearchParam();
                param.setName(nextParamUnchainedName);

//				if (StringUtils.isNotBlank(chain)) {
//					param.addChain(chain);
//				}
//
//				if (nextParameter.getParamType() == RestSearchParameterTypeEnum.REFERENCE) {
//					for (String nextWhitelist : new TreeSet<String>(nextParameter.getQualifierWhitelist())) {
//						if (nextWhitelist.startsWith(".")) {
//							param.addChain(nextWhitelist.substring(1));
//						}
//					}
//				}

                param.setDocumentation(nextParamDescription);
                if (nextParameter.getParamType() != null) {
                    param.getTypeElement().setValueAsString(nextParameter.getParamType().getCode());
                }
                for (Class<? extends IBaseResource> nextTarget : nextParameter.getDeclaredTypes()) {
                    RuntimeResourceDefinition targetDef = serverConfiguration.getFhirContext().getResourceDefinition(nextTarget);
                    if (targetDef != null) {
                        ResourceType code;
                        try {
                            code = ResourceType.fromCode(targetDef.getName());
                        } catch (FHIRException e) {
                            code = null;
                        }
//						if (code != null) {
//							param.addTarget(targetDef.getName());
//						}
                    }
                }
            }
        }
    }

    @Initialize
    public void initializeOperations() {
        myOperationBindingToName = new IdentityHashMap<OperationMethodBinding, String>();
        myOperationNameToBindings = new HashMap<String, List<OperationMethodBinding>>();

        Map<String, List<BaseMethodBinding<?>>> resourceToMethods = collectMethodBindings();
        for (Map.Entry<String, List<BaseMethodBinding<?>>> nextEntry : resourceToMethods.entrySet()) {
            List<BaseMethodBinding<?>> nextMethodBindings = nextEntry.getValue();
            for (BaseMethodBinding<?> nextMethodBinding : nextMethodBindings) {
                if (nextMethodBinding instanceof OperationMethodBinding) {
                    OperationMethodBinding methodBinding = (OperationMethodBinding) nextMethodBinding;
                    if (myOperationBindingToName.containsKey(methodBinding)) {
                        continue;
                    }

                    String name = createOperationName(methodBinding);
                    log.info("Detected operation: {}", name);

                    myOperationBindingToName.put(methodBinding, name);
                    if (myOperationNameToBindings.containsKey(name) == false) {
                        myOperationNameToBindings.put(name, new ArrayList<OperationMethodBinding>());
                    }
                    myOperationNameToBindings.get(name).add(methodBinding);
                }
            }
        }
    }

    private String createOperationName(OperationMethodBinding theMethodBinding) {
        StringBuilder retVal = new StringBuilder();
        if (theMethodBinding.getResourceName() != null) {
            retVal.append(theMethodBinding.getResourceName());
        }

        retVal.append('-');
        if (theMethodBinding.isCanOperateAtInstanceLevel()) {
            retVal.append('i');
        }
        if (theMethodBinding.isCanOperateAtServerLevel()) {
            retVal.append('s');
        }
        retVal.append('-');

        // Exclude the leading $
        retVal.append(theMethodBinding.getName(), 1, theMethodBinding.getName().length());

        return retVal.toString();
    }

    private void sortRuntimeSearchParameters(List<RuntimeSearchParam> searchParameters) {
        Collections.sort(searchParameters, new Comparator<RuntimeSearchParam>() {
            @Override
            public int compare(RuntimeSearchParam theO1, RuntimeSearchParam theO2) {
                return theO1.getName().compareTo(theO2.getName());
            }
        });
    }


}

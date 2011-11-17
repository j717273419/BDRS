package au.com.gaiaresources.bdrs.controller.record;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import au.com.gaiaresources.bdrs.controller.AbstractController;
import au.com.gaiaresources.bdrs.controller.attribute.formfield.FormField;
import au.com.gaiaresources.bdrs.controller.attribute.formfield.FormFieldFactory;
import au.com.gaiaresources.bdrs.controller.attribute.formfield.RecordProperty;
import au.com.gaiaresources.bdrs.controller.attribute.formfield.RecordPropertyType;
import au.com.gaiaresources.bdrs.controller.insecure.taxa.ComparePersistentImplByWeight;
import au.com.gaiaresources.bdrs.model.location.Location;
import au.com.gaiaresources.bdrs.model.location.LocationDAO;
import au.com.gaiaresources.bdrs.model.location.LocationNameComparator;
import au.com.gaiaresources.bdrs.model.metadata.Metadata;
import au.com.gaiaresources.bdrs.model.metadata.MetadataDAO;
import au.com.gaiaresources.bdrs.model.method.Taxonomic;
import au.com.gaiaresources.bdrs.model.record.Record;
import au.com.gaiaresources.bdrs.model.record.RecordDAO;
import au.com.gaiaresources.bdrs.model.survey.Survey;
import au.com.gaiaresources.bdrs.model.survey.SurveyDAO;
import au.com.gaiaresources.bdrs.model.taxa.Attribute;
import au.com.gaiaresources.bdrs.model.taxa.AttributeScope;
import au.com.gaiaresources.bdrs.model.taxa.AttributeType;
import au.com.gaiaresources.bdrs.model.taxa.AttributeValue;
import au.com.gaiaresources.bdrs.model.taxa.IndicatorSpecies;
import au.com.gaiaresources.bdrs.model.taxa.TaxaDAO;
import au.com.gaiaresources.bdrs.security.Role;
import au.com.gaiaresources.bdrs.service.web.AtlasService;
import edu.emory.mathcs.backport.java.util.Collections;

@Controller
public class AtlasController extends AbstractController {

    private Logger log = Logger.getLogger(getClass());
    
    public static final String ATLAS_URL = "/bdrs/user/atlas.htm";
    
    public static final String TAXON_GROUP_ATTRIBUTE_PREFIX = "taxonGroupAttr_";
    public static final String CENSUS_METHOD_ATTRIBUTE_PREFIX = "censusMethodAttr_";

    @Autowired
    private RecordDAO recordDAO;
    @Autowired
    private SurveyDAO surveyDAO;
    @Autowired
    private TaxaDAO taxaDAO;
    @Autowired
    private LocationDAO locationDAO;
    @Autowired
    private AtlasService atlasService;
    @Autowired
    private MetadataDAO metadataDAO;

    private FormFieldFactory formFieldFactory = new FormFieldFactory();

    /**
     * Note there is no POST method - the atlas form posts to the TrackerController
     * 
     * @param request
     * @param response
     * @param surveyId
     * @param taxonSearch
     * @param recordId
     * @param guid
     * @return
     */
    @RolesAllowed( {  Role.USER, Role.POWERUSER, Role.SUPERVISOR, Role.ADMIN })
    @RequestMapping(value = ATLAS_URL, method = RequestMethod.GET)
    public ModelAndView addRecord(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(value = "surveyId", required = true) int surveyId,
            @RequestParam(value = "taxonSearch", required = false) String taxonSearch,
            @RequestParam(value = "recordId", required = false, defaultValue = "0") int recordId,
            @RequestParam(value = "guid", required = false) String guid) {
        
        Survey survey = surveyDAO.getSurvey(surveyId);
        Record record = recordDAO.getRecord(recordId);
        
        record = record == null ? new Record() : record;
        
        // Set record visibility to survey default. Setting via web form not supported.
        // Survey's default record visibility can be set in the 'admin -> projects' interface
        record.setRecordVisibility(survey.getDefaultRecordVisibility());
        
        IndicatorSpecies species = null;
        
        if (guid != null && !guid.isEmpty()) {
            species = taxaDAO.getIndicatorSpeciesByGuid(guid);
        } 
        if (species == null && taxonSearch != null && !taxonSearch.isEmpty()) {
            List<IndicatorSpecies> speciesList = surveyDAO.getSpeciesForSurveySearch(surveyId, taxonSearch);
            if (speciesList.isEmpty()) {
                species = null;
            } else if (speciesList.size() == 1) {
                species = speciesList.get(0);
            } else {
                log.warn("Multiple species found for survey " + surveyId
                        + " and taxon search \"" + taxonSearch
                        + "\". Using the first.");
                species = speciesList.get(0);
            }
        }
        if(species == null && record.getSpecies() != null) {
            species = record.getSpecies();
        }

        ModelAndView mv;
        if(species == null) {
            Map<String, String> errorMap = (Map<String, String>)getRequestContext().getSessionAttribute("errorMap");
            if (guid != null && !guid.isEmpty()) {
                species = atlasService.importSpecies(guid, true, errorMap);
            } 
        }
        
        if (species == null) {
            log.debug("Could not determine species, reverting to tracker form");
            // The atlas form relies upon a preconfigured species.
            // If we do not have one, fall back to the tracker form.
            mv = new ModelAndView(new RedirectView("tracker.htm"));
            mv.addAllObjects(request.getParameterMap());
            mv.addObject("surveyId", surveyId);
            return mv;
        } else {
            // Add all attribute form fields
            Map<RecordPropertyType, FormField> formFieldMap = new HashMap<RecordPropertyType, FormField>();

            // Add all property form fields
            for (RecordPropertyType type : RecordPropertyType.values()) {
            	RecordProperty recordProperty = new RecordProperty(survey, type, metadataDAO);
            	formFieldMap.put(type,  formFieldFactory.createRecordFormField(record, recordProperty, species, Taxonomic.TAXONOMIC));
            }
            
            // Determine the file attribute to use for the form (if there is one)
            // Sort the list of survey attributes by weight so that we can
            // correctly select the first file attribute.
            List<Attribute> attributeList = survey.getAttributes();
            Collections.sort(attributeList, new ComparePersistentImplByWeight());

            // Retrieve the first file attribute and if present, the associated
            // record attribute.
            Attribute fileAttr = null;
            AttributeValue fileRecAttr = null;
            for(Attribute attr : attributeList) {
                if(!AttributeScope.LOCATION.equals(attr.getScope()) && fileAttr == null && AttributeType.FILE.equals(attr.getType())) {
                    // Attribute found.
                    fileAttr = attr;
                    // Try to locate matching record attribute
                    for(AttributeValue recAttr : record.getAttributes()) {
                        if(fileRecAttr == null && fileAttr.equals(recAttr.getAttribute())) {
                            fileRecAttr = recAttr;
                        }
                    }
                }
            }
            
            FormField fileFormField = formFieldFactory.createRecordFormField(survey, record, fileAttr, fileRecAttr);
            
            Map<String, String> errorMap = (Map<String, String>)getRequestContext().getSessionAttribute("errorMap");
            getRequestContext().removeSessionAttribute("errorMap");
            Map<String, String> valueMap = (Map<String, String>)getRequestContext().getSessionAttribute("valueMap");
            getRequestContext().removeSessionAttribute("valueMap");
            
            Metadata predefinedLocationsMD = survey.getMetadataByKey(Metadata.PREDEFINED_LOCATIONS_ONLY);
            boolean predefinedLocationsOnly = predefinedLocationsMD != null && 
                Boolean.parseBoolean(predefinedLocationsMD.getValue());
            
            Set<Location> locations = new TreeSet<Location>(new LocationNameComparator());
            locations.addAll(survey.getLocations());
            if(!predefinedLocationsOnly) {
                locations.addAll(locationDAO.getUserLocations(getRequestContext().getUser()));
            }
            
            Metadata defaultLocId = getRequestContext().getUser().getMetadataObj(Metadata.DEFAULT_LOCATION_ID);
            Location defaultLocation;
            if(defaultLocId == null) {
                defaultLocation = null;
            } else {
                int defaultLocPk = Integer.parseInt(defaultLocId.getValue());
                defaultLocation = locationDAO.getLocation(defaultLocPk);
            }
            
            mv = new ModelAndView("atlas");
            mv.addObject("record", record);
            mv.addObject("taxon", species);
            mv.addObject("survey", survey);
            mv.addObject("locations", locations);
            mv.addObject("formFieldMap", formFieldMap);
            mv.addObject("fileFormField", fileFormField);
            mv.addObject("preview", request.getParameter("preview") != null);
            mv.addObject("defaultLocation", defaultLocation);
            
            mv.addObject("errorMap", errorMap);
            mv.addObject("valueMap", valueMap);
        }
        
        return mv;
    }
}
package com.justransform.api_NEW.api.controllers;

import com.justransform.app.base.services.ResourceService;
import com.justransform.data.repositories.ResourceRepository;
import com.justransform.edi.services.XsdReadService;
import com.justransform.edi.xml.XmlFromXsdTool;
import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.XSInstance;
import jlibs.xml.xsd.XSParser;
import org.apache.commons.io.IOUtils;
import org.apache.xerces.xs.XSModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequestMapping("/xsd")
public class XsdController {

    private static final String PREFIX = "stream2file";
    private static final String SUFFIX = ".tmp";

    private XsdReadService xsdReadService;
    private ResourceService resourceService;
    private ResourceRepository resourceRepository;

    @Autowired
    private XsdController(XsdReadService xsdReadService, ResourceService resourceService, ResourceRepository resourceRepository) {
        this.xsdReadService = xsdReadService;
        this.resourceService = resourceService;
        this.resourceRepository = resourceRepository;
    }

    @PostMapping(path="/generate", produces = "application/xml", consumes = "application/xml")
    public ResponseEntity<String> generateXml(
            @RequestParam(name = "minimum", required = false, defaultValue = "0") int minimum,
            @RequestParam(name = "maximum", required = false, defaultValue = "0") int maximum,
            @RequestParam(name = "optional", required = false, defaultValue = "false") boolean optional,
            @RequestBody String xsd
    ) {
        try {
            File tempFile = stream2file(IOUtils.toInputStream(xsd, StandardCharsets.UTF_8));
            XSModel xsModel = new XSParser().parse(tempFile.getAbsolutePath());
            XmlFromXsdTool tool = new XmlFromXsdTool(tempFile);
            // Generate XML
            XSInstance xsInstance = new XSInstance();
            xsInstance.minimumElementsGenerated = minimum;
            xsInstance.maximumElementsGenerated = maximum;
            xsInstance.generateOptionalElements = optional; // null means random

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            XMLDocument sampleXml = new XMLDocument(new StreamResult(outputStream), true, 4, null);
            QName root = new QName(tool.getSchema().getTargetNamespace(), tool.getPotentialRootNames().get(0));
            xsInstance.generate(xsModel, root, sampleXml);
            return ResponseEntity.ok(outputStream.toString());
        } catch (IOException | TransformerConfigurationException | SAXException e) {
            e.printStackTrace();
        }

        return null;
    }

    @PostMapping(path="/validate", produces = "application/text", consumes = "application/xml")
    public ResponseEntity<String> generateXml(
            @RequestParam("resource") long resourceId,
            @RequestBody String xml
    ) {
        try {
            Optional<Resource> resource = resourceRepository.findById(resourceId);
            if(resource.isPresent()){
                Optional<ResourceFile> resourceFile = resourceService.getResourceContentUnchecked(resource.get().getOwner(), resourceId);
                if(resourceFile.isPresent()) {
                    File xsdFile = stream2file(resourceFile.get().getContent());
                    File xmlFile = stream2file(IOUtils.toInputStream(xml, StandardCharsets.UTF_8));
                    Util.validate(new FileReader(xmlFile), xsdFile);

                    return ResponseEntity.ok("Valid");
                }
            }

        } catch (IOException | SAXException e) {
            e.printStackTrace();
            return ResponseEntity.ok("Not Valid because: " + e.getLocalizedMessage());
        }

        return null;
    }

    private File stream2file (InputStream in) throws IOException {
        final File tempFile = File.createTempFile(PREFIX, SUFFIX);
        tempFile.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            FileCopyUtils.copy(in, out);
        }
        return tempFile;
    }

}

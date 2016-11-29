package io.pivotal.demo.bosh;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("bosh")
public class BoshController {

    public static final Logger LOG = LoggerFactory.getLogger(BoshController.class);

    @Autowired
    private RestTemplate _oauth2template;

    @Value("${bosh.director}")
    private String _boshUrl;

    @Value("${bosh.ssh.enabled}")
    private boolean _sshEnabled;

    @Value("${bosh.ssh.gcp.project:project}")
    private String _gcpProject;

    private final static MessageFormat GCP_SSH_URL = new MessageFormat("https://ssh.cloud.google.com/projects/{0}/zones/us-east1-b/instances/{1}?authuser=1");


    private HttpEntity<String> buildEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        return new HttpEntity<String>("parameters", headers);
    }

    @RequestMapping("/info")
    @ResponseBody
    public ResponseEntity info() {
        LOG.debug("Getting BOSH info...");
        ResponseEntity response = _oauth2template.exchange(_boshUrl + "info",
                HttpMethod.GET, buildEntity(), String.class);
        LOG.debug("Info Response: " + response);
        return response;
    }

    @RequestMapping("/deployments")
    @ResponseBody
    public ResponseEntity deployments() throws Exception {
        ResponseEntity response = _oauth2template.exchange(_boshUrl + "deployments",
                HttpMethod.GET, buildEntity(), String.class);
        LOG.debug("Deployments Response: " + response);
        return response;
    }

    @RequestMapping("/deployment/{name}")
    @ResponseBody
    public ResponseEntity deployment(@PathVariable String name) throws Exception {
        ResponseEntity response = _oauth2template.exchange(_boshUrl + "deployments/" + name,
                HttpMethod.GET, buildEntity(), String.class);
        LOG.debug("Deployment Response: " + response);
        return response;
    }

    @RequestMapping(value="/deployment/{name}/instances", produces="application/json")
    @ResponseBody
    public List<Map> instances(@PathVariable String name) throws Exception {
        LOG.debug("Getting instance info for " + name);
        ResponseEntity response = _oauth2template.exchange(_boshUrl + "deployments/" + name + "/instances?format=full",
                HttpMethod.GET, buildEntity(), String.class);
        LOG.debug("Instances Response: " + response);

        //this will return 302, retrieve task ID and pull status
        String location = response.getHeaders().getLocation().getPath();
        LOG.debug("Location path for task: " + location);

        boolean done = false;
        ObjectMapper mapper = new ObjectMapper();
        while(!done) {
            Thread.sleep(100);
            response = _oauth2template.exchange(_boshUrl + location,
                    HttpMethod.GET, buildEntity(), String.class);
            LOG.debug("Task response: " + response);
            Map result = mapper.readValue(response.getBody().toString(), Map.class);
            done = (StringUtils.endsWithIgnoreCase((String) result.getOrDefault("state", "pending"), "done")) ? true : false;
        }


        response = _oauth2template.exchange(_boshUrl + location + "/output?type=result",
                HttpMethod.GET, buildEntity(), String.class);

        List<Map> jsons = mergeJsons(response.getBody().toString());
        for(Map m : jsons) {
            //add ssh key if needed
            if(_sshEnabled) addSSH(m);
            LOG.debug("Instances Response Json: " + m);
        }
        return jsons;
    }

    @RequestMapping(value="/deployment/{name}/vms", produces="application/json")
    @ResponseBody
    public ResponseEntity vms(@PathVariable String name) throws Exception {
        LOG.debug("Getting instance info for " + name);
        ResponseEntity response = _oauth2template.exchange(_boshUrl + "deployments/" + name + "/vms",
                HttpMethod.GET, buildEntity(), String.class);
        LOG.debug("Instances Response: " + response);
        return response;

        //this will return 302, retrieve task ID and pull status
//        String location = response.getHeaders().getLocation().getPath();
//        LOG.debug("Location path for task: " + location);
//
//        boolean done = false;
//        ObjectMapper mapper = new ObjectMapper();
//        while(!done) {
//            Thread.sleep(100);
//            response = _oauth2template.exchange(_boshUrl + location,
//                    HttpMethod.GET, buildEntity(), String.class);
//            LOG.debug("Task response: " + response);
//            Map result = mapper.readValue(response.getBody().toString(), Map.class);
//            done = (StringUtils.endsWithIgnoreCase((String) result.getOrDefault("state", "pending"), "done")) ? true : false;
//        }
//
//
//        response = _oauth2template.exchange(_boshUrl + location + "/output?type=result",
//                HttpMethod.GET, buildEntity(), String.class);
//        LOG.debug("Instances Response: " + response);
//        return mergeJsons(response.getBody().toString());
    }

    protected List<Map> mergeJsons(String jsonStr) throws Exception {
        String[] lines = jsonStr.split(System.getProperty("line.separator"));
        ObjectMapper mapper = new ObjectMapper();
        List<Map> jsons = new ArrayList<>();
        for(int i=0; i<lines.length; i++) {
            jsons.add(mapper.readValue(lines[i], Map.class));
        }
        return jsons;
    }

    protected void addSSH(Map m) {
        String vm = (String) m.get("vm_cid");
        if(vm != null) {
            Object[] vars = {_gcpProject, vm};
            m.put("ssh_url", GCP_SSH_URL.format(vars));
        }
    }
}

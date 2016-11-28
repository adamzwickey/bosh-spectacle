//Initial Structure of Tree
var treeData = [
    {
        "name":"BOSH Director",
        "type":"bosh",
        "parent":null,
        "children":[{}]
    }];

//Load initial data from server
$.ajax({
    url: "/bosh/deployments"
}).then(function(data) {

    //loop through each deployment and build tree
    $.each(data, function(key, value) {
        var deployment = {
            name: value.name,
            type: "deployment",
            _children:[
                {
                    "name":"Jobs",
                    "type": "vm-group"
                },
                {
                    "name":"Releases",
                    "type": "release",
                    "data": value.releases
                },
                {
                    "name":"Stemcells",
                    "type": "sc",
                    "data": value.stemcells
                },
                {
                    "name":"Manifest",
                    "type": "manifest"
                }
            ]

        };
        treeData[0].children.push(deployment);
    });
    //pop off first one when we are done since this was placeholder
    treeData[0].children.shift();
    update(root);
});



var margin = {top: 20, right: 120, bottom: 20, left:100},
    width = 1000 - margin.right - margin.left,
    height = 450 - margin.top - margin.bottom;

var i = 0,
    duration = 750,
    root;

var tree = d3.layout.tree()
    .size([height, width]);

var diagonal = d3.svg.diagonal()
    .projection(function(d) { return [d.y, d.x]; });

var svg = d3.select("#tree").append("svg")
    .attr("width", width + margin.right + margin.left)
    .attr("height", height + margin.top + margin.bottom)
    .append("g")
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");


root = treeData[0];
root.x0 = height / 2;
root.y0 = 0;

//update(root);

d3.select(self.frameElement).style("height", "800px");

function update(source) {
    console.log(source);

    //preload details if this is a deployment
    if(source.type == "deployment" && source.children[0]._children == null) {

        console.log("preloading VM info...");
        $.ajax({
            url: "/bosh/deployment/" + source.name + "/instances",
        }).then(function(data) {

            //loop through each job and keep track of things
            var deployment = [];
            var jobMap = new Map();
            $.each(data, function(key, value) {

                if(value.job_state == null) {
                    var errands = jobMap.get("Errands");
                    if(!errands) {
                        errands = {
                            name: "Errands",
                            type: "errand",
                            _children: []
                        };
                    }

                    var model = {
                        name: value.job_name,
                        type: "errand",
                        data: value
                    };
                    errands._children.push(model);
                    jobMap.set("Errands",errands);

                } else if(jobMap.has(value.job_name)) {
                    var jobs = jobMap.get(value.job_name);
                    var model = {
                        name: value.job_name + "/" + value.index,
                        type: "job",
                        data: value
                    };
                    jobs._children.push(model);
                    jobMap.set(value.job_name,jobs);

                } else {
                    var jobs = {
                        name: value.job_name,
                        type: "job-pool",
                        _children: []
                     };

                    var model = {
                        name: value.job_name + "/" + value.index,
                        type: "job",
                        data: value
                    };
                    jobs._children.push(model);
                    jobMap.set(value.job_name,jobs);
                }
            });

            jobMap.forEach(function(value, key) { deployment.push(value);  });

            source.children[0]._children = deployment;
            console.log("Done preloading...");
            update(source);
        });
    }

    // Compute the new tree layout.
    var nodes = tree.nodes(root).reverse(),
        links = tree.links(nodes);

    // Normalize for fixed-depth.
    nodes.forEach(function(d) { d.y = d.depth * 180; });

    // Update the nodes…
    var node = svg.selectAll("g.node")
        .data(nodes, function(d) { return d.id || (d.id = ++i); });

    // Enter any new nodes at the parent's previous position.
    var nodeEnter = node.enter().append("g")
        .attr("class", "node")
        .attr("transform", function(d) { return "translate(" + source.y0 + "," + source.x0 + ")"; })
        .on("click", click);


    nodeEnter.append("image")
        .attr("xlink:href", function(d) {
            var img = "bubble.png";
            if(d.type == "job") {
                img = (d.data.job_state == "running") ? "sun.png" : "cloud.png";
            }
            return img;
        })
        .attr("x", -12)
        .attr("y", -12)
        .attr("width", 24)
        .attr("height", 24);

    nodeEnter.append("text")
        .attr("x", function(d) { return d.children || d._children ? -13 : 13; })
        .attr("dy", ".35em")
        .attr("text-anchor", function(d) { return d.children || d._children ? "end" : "start"; })
        .text(function(d) { return d.name; })
        .style("fill-opacity", 1e-6)
        .attr("class", function(d) {
            if (d.data != null) { return 'hyper'; }
        })
        .on("click", function (d) {
            $('.hyper').attr('style', 'font-weight:normal');
            d3.select(this).attr('style', 'font-weight:bold');

            if (d.data != null) {
                $("#details-title").empty();
                $("#details-title").removeClass("hidden");
                $("#details").removeClass("hidden");
                switch(d.type) {
                    case "job":
                        //hide others
                        $("#release-details").addClass("hidden");
                        $("#errand-details").addClass("hidden");
                        $("#stemcell-details").addClass("hidden");

                        //show job
                        $("#job-details").removeClass("hidden");
                        $("#job-details").empty();


                        $("#details-title").append("Job Details [" + d.name + "]");
                        var html = "<div class='row'>";

                        html += "<div class='col-md-6'><b>ID:</b> " + d.data.id + "<br>" +
                            "<b>Availability Zone:</b> " + d.data.az + "<br>" +
                            "<b>IP Address:</b> " + d.data.ips + "<br>" +
                            "<b>Job State:</b> " + d.data.job_state + "<br>" +
                            "<b>VM Type:</b> " + d.data.vm_type + "<br>" +
                            "<b>Attached Disk CID:</b> " + d.data.disk_cid + "<br>" +
                            "<b>VM CID:</b> " + d.data.vm_cid + "</div>";

                        //VM Vitals
                        html += "<div class='col-md-6'><b>CPU:</b> sys[" + d.data.vitals.cpu.sys + "] user[" + d.data.vitals.cpu.user + "] wait[" + d.data.vitals.cpu.wait + "]<br>" +
                            "<b>Memory:</b> " + (d.data.vitals.mem.kb/1024).toFixed(2) + "MB (" + d.data.vitals.mem.percent + "%)<br>" +
                            "<b>Swap:</b> " + (d.data.vitals.swap.kb/1024).toFixed(2) + "MB (" + d.data.vitals.swap.percent + "%)<br>" +
                            "<b>Disk:</b> ephemeral[" + d.data.vitals.disk.ephemeral.percent + "%] system[" + d.data.vitals.disk.system.percent + "%] ";
                        if(d.data.vitals.disk.persistent) html += "persistent[" + d.data.vitals.disk.persistent.percent + "%]";
                        html += "</div>";

                        html += "</div>";

                        html += "<div class='col-md-12'><hr></div>";

                        html += "<div class='row'>";
                        $.each(d.data.processes, function(key, value) {

                            var panelType = "panel-success";
                            var glyphicon = "glyphicon-thumbs-up";
                            if(value.state != "running") {
                                panelType = "panel-danger";
                                glyphicon = "glyphicon-thumbs-down";
                                value.uptime.secs = 0;
                            }
                            html += "<div class='col-md-3'><div class='panel " + panelType + "'>";
                            html += "<div class='panel-heading'><h3 class='panel-title'><span class='glyphicon " + glyphicon + "' aria-hidden='true'></span>&nbsp" + value.name + "</h3></div>";
                            html += "<div class='panel-body'><b>CPU:</b> " + value.cpu.total + " <br><b>Memory:</b> " + (value.mem.kb/1024).toFixed(2)  + "&nbspMB&nbsp(" + value.mem.percent + "%)<br><b>Uptime:</b> " + value.uptime.secs + "&nbspSecs.</div>";
                            html += "</div></div>";
                        });
                        html += "</row>"
                        $("#job-details").append(html);
                        break;

                    case  "release":
                        //hide others
                        $("#job-details").addClass("hidden");
                        $("#errand-details").addClass("hidden");
                        $("#stemcell-details").addClass("hidden");

                        //show release
                        $("#release-details").removeClass("hidden");
                        $("#release-details").empty();

                        $("#details-title").append("Releases [" + d.parent.name + "]");
                        var releaseHtml = "<table class='table table-striped'><thead><tr><th>Release</th><th>Version</th></tr></thead><tbody>";
                        $.each(d.data, function(key, value) {
                            releaseHtml += "<tr><td>" + value.name + "</td><td>" + value.version + "</td></tr>";
                        });
                        releaseHtml += "</tbody></table>";
                        $("#release-details").append(releaseHtml);
                        break;

                    case  "sc":
                        //hide others
                        $("#job-details").addClass("hidden");
                        $("#errand-details").addClass("hidden");
                        $("#release-details").addClass("hidden");

                        //show SC
                        $("#stemcell-details").removeClass("hidden");
                        $("#stemcell-details").empty();

                        $("#details-title").append("Stemcells [" + d.parent.name + "]");
                        var html = "<table class='table table-striped'><thead><tr><th>Release</th><th>Version</th></tr></thead><tbody>";
                        $.each(d.data, function(key, value) {
                            html += "<tr><td>" + value.name + "</td><td>" + value.version + "</td></tr>";
                        });
                        html += "</tbody></table>";
                        $("#stemcell-details").append(html);
                        break;

                    case  "manifest":
                        break;
                    case  "errand":
                        break;
                }


            }
        })
    ;

    // Transition nodes to their new position.
    var nodeUpdate = node.transition()
        .duration(duration)
        .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; });

    nodeUpdate.select("image")
        .attr("xlink:href", function(d) {
            var img = "bubble.png";
            if(d.type == "job") {
                img = (d.data.job_state == "running") ? "sun.png" : "cloud.png";
            }
            return img;
        })
        .attr("x", -12)
        .attr("y", -12)
        .attr("width", 24)
        .attr("height", 24);

    nodeUpdate.select("text")
        .style("fill-opacity", 1);

    // Transition exiting nodes to the parent's new position.
    var nodeExit = node.exit().transition()
        .duration(duration)
        .attr("transform", function(d) { return "translate(" + source.y + "," + source.x + ")"; })
        .remove();

    nodeExit.select("circle")
        .attr("r", 1e-6);

    nodeExit.select("text")
        .style("fill-opacity", 1e-6);

    // Update the links…
    var link = svg.selectAll("path.link")
        .data(links, function(d) { return d.target.id; });

    // Enter any new links at the parent's previous position.
    link.enter().insert("path", "g")
        .attr("class", "link")
        .attr("d", function(d) {
            var o = {x: source.x0, y: source.y0};
            return diagonal({source: o, target: o});
        });

    // Transition links to their new position.
    link.transition()
        .duration(duration)
        .attr("d", diagonal);

    // Transition exiting nodes to the parent's new position.
    link.exit().transition()
        .duration(duration)
        .attr("d", function(d) {
            var o = {x: source.x, y: source.y};
            return diagonal({source: o, target: o});
        })
        .remove();

    // Stash the old positions for transition.
    nodes.forEach(function(d) {
        d.x0 = d.x;
        d.y0 = d.y;
    });
}

// Toggle children on click.
function click(d) {
    if (d.children) {
        d._children = d.children;
        d.children = null;
    } else {
        d.children = d._children;
        d._children = null;
    }
    update(d);
}




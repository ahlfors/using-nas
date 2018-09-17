package rzhang.nas.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class TimedCrawlerConfig {

    @JsonProperty(required = true)
    public String triggerTime;

    @JsonProperty(required = true)
    public String triggerName;

    @JsonProperty("payload")
    public String payload;
}

class CrawlerConfig {

    @JsonProperty(required = true)
    public String url;

    @JsonProperty(required = true)
    public String rootDir;

    @JsonProperty(defaultValue = "1")
    public int numberOfPages;

    @JsonProperty(defaultValue = "10")
    public int cutoffSize;

    @JsonProperty
    public boolean debug;
}

package rzhang.nas.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class CrawlingResult {

    @JsonProperty(defaultValue = "")
    public String errorStack;

    @JsonProperty()
    public int totalCrawlCount;

    @JsonProperty()
    public long totalImageSize;

    @JsonProperty()
    public long totalImageDownloaded;
}

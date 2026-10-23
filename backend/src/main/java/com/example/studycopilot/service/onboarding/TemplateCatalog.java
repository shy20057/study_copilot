package com.example.studycopilot.service.onboarding;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class TemplateCatalog {
    public record Definition(String id,String name,String description,int minimumDays,List<String> stages) {}
    public record Item(String id,String title,String subjectAlias,int learningDayOffset,int plannedPomodoros) {}
    public record Resource(String version,List<Definition> templates,List<Item> items) {}
    private final Resource resource;
    public TemplateCatalog(ObjectMapper json) {
        try(var input=new ClassPathResource("templates/phase-a-study-templates.v1.json").getInputStream()) {resource=json.readValue(input,Resource.class);}
        catch(Exception ex){throw new IllegalStateException("Cannot load study templates",ex);}
        if(resource.items().stream().anyMatch(i->i.learningDayOffset()<0 || i.learningDayOffset()>6 || i.plannedPomodoros()<1)
            ||resource.items().stream().map(Item::id).distinct().count()!=resource.items().size()) throw new IllegalStateException("Invalid template item catalog");
    }
    public List<Definition> list(){return resource.templates();}
    public String version(){return resource.version();}
    public List<Item> items(){return resource.items();}
    public Definition find(String id){return list().stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(()->new BusinessException(ScErrorCode.NOT_FOUND));}
}

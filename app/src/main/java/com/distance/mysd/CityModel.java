package com.distance.mysd;

public class CityModel {
    private String name;
    private Long cases;
    private Long level;


    public CityModel(){}

    private CityModel(String name, Long cases, Long level) {
        this.name = name;
        this.cases = cases;
        this.level = level;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCases() {
        return cases;
    }

    public void setCases(Long cases) {
        this.cases = cases;
    }

    public Long getLevel() {
        return level;
    }

    public void setLevel(Long level) {
        this.level = level;
    }
}

package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;
import lombok.Data;

@Data
public class Person {
    private String name;
    private int age;
    private String email;
    private String address;

    public Person() {
        name = "John Doe";
    }

    public String walk() {
        return name + " is walking.";
    }
    public String walk(String name) {
        return name + " is walking.";
    }









}

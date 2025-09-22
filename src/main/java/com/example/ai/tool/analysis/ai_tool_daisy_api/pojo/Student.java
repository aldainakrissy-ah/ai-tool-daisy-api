package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;


import lombok.*;

import java.util.*;

@Data

@EqualsAndHashCode(callSuper = false)
public class Student extends Person {
    private String studentId;
    private String major;
    private double gpa;


    public Student() {
        super();
    }

    @Override
    public String walk() {
        return getName() + " is walking to class.";
    }

    public static void main(String[] args) {

        Student student1 = new Student();
        student1.setName("Jane Doe");
        student1.setStudentId("S12345");

        Student student2 = new Student();
        student2.setName("Jane");
        student2.setStudentId("asdasd");

        Person person1 = new Student();
        System.out.println(person1.walk(person1.getName()));
        System.out.println(student1.walk());

        HashMap<String,String> studentMap = new HashMap<>();
        studentMap.put("wqe", student1.getStudentId());
        studentMap.put("qwe", student1.getName());

        HashMap<String,String> studentMap1 = new HashMap<>();
        studentMap1.put("wwq", student2.getStudentId());
        studentMap1.put("ww", student2.getName());
        studentMap.putAll(studentMap1);


        studentMap.forEach((key, value) -> {
            System.out.println("Key: " + key + ", Value: " + value);
        });

        Map<String, String> capitalCities = new HashMap<>();
        capitalCities.put("England", "London");
        capitalCities.put("India", "New Dehli");
        capitalCities.put("Austria", "Wien");
        capitalCities.put("Norway", "Oslo");
        capitalCities.put("Norway", "Oslo");  // Duplicate
        capitalCities.put("USA", "Washington DC");

        System.out.println("TreeMap: " + capitalCities);

        //System.out.println("Student Map: " + studentMap.get("studentId") + ", " + studentMap.get("name"));


    }
}

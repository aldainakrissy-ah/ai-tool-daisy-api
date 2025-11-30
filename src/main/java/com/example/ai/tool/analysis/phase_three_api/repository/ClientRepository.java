package com.example.ai.tool.analysis.phase_three_api.repository;

import com.example.ai.tool.analysis.phase_three_api.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
}

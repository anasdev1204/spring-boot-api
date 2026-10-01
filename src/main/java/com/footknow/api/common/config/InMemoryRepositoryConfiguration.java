package com.footknow.api.common.config;

import com.footknow.api.common.repository.CreateRepository;
import com.footknow.api.common.repository.InMemoryCreateRepository;

import com.footknow.api.endpoints.league.League;
import com.footknow.api.endpoints.team.Team;
import com.footknow.api.endpoints.player.Player;
import com.footknow.api.endpoints.coach.Coach;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("in-memory & !production")
public class InMemoryRepositoryConfiguration {

    @Bean
    public CreateRepository<League> leagueRepository() {
        return new InMemoryCreateRepository<>();
    }

    @Bean
    public CreateRepository<Team> teamRepository() {
        return new InMemoryCreateRepository<>();
    }

    @Bean
    public CreateRepository<Player> playerRepository() {
        return new InMemoryCreateRepository<>();
    }

    @Bean
    public CreateRepository<Coach> coachRepository() {
        return new InMemoryCreateRepository<>();
    }
}
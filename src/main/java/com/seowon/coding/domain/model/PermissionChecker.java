package com.seowon.coding.domain.model;


import lombok.Builder;

import java.util.List;

class PermissionChecker {

    /**
     * TODO #7: 코드를 최적화하세요
     * 테스트 코드`PermissionCheckerTest`를 활용하시면 리펙토링에 도움이 됩니다.
     */
    public static boolean hasPermission(
            String userId,
            String targetResource,
            String targetAction,
            List<User> users,
            List<UserGroup> groups,
            List<Policy> policies
    ) {

        List<String> groupIdList = users.stream()
                .filter(u -> u.id.equals(userId))
                .flatMap(u -> u.groupIds.stream())
                .toList();

        List<String> policyIdList = groups.stream()
                .filter(g -> groupIdList.contains(g.id))
                .flatMap(g -> g.policyIds.stream())
                .toList();


        List<Statement> statementList = policies.stream()
                .filter(p ->policyIdList.contains(p.id))
                .flatMap(p -> p.statements.stream())
                .toList();

        return statementList.stream()
                .anyMatch(s -> s.resources.contains(targetResource) &&  s.actions.contains(targetAction));
    }
}

class User {
    String id;
    List<String> groupIds;

    public User(String id, List<String> groupIds) {
        this.id = id;
        this.groupIds = groupIds;
    }
}

class UserGroup {
    String id;
    List<String> policyIds;

    public UserGroup(String id, List<String> policyIds) {
        this.id = id;
        this.policyIds = policyIds;
    }
}

class Policy {
    String id;
    List<Statement> statements;

    public Policy(String id, List<Statement> statements) {
        this.id = id;
        this.statements = statements;
    }
}

class Statement {
    List<String> actions;
    List<String> resources;

    @Builder
    public Statement(List<String> actions, List<String> resources) {
        this.actions = actions;
        this.resources = resources;
    }
}
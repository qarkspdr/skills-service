package skills.services

import callStack.profiler.Profile
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import skills.controller.exceptions.SkillException
import skills.controller.request.model.ActionPatchRequest
import skills.controller.request.model.BadgeRequest
import skills.controller.result.model.GlobalBadgeLevelRes
import skills.controller.result.model.GlobalBadgeResult
import skills.controller.result.model.ProjectResult
import skills.controller.result.model.SkillDefPartialRes
import skills.services.settings.SettingsService
import skills.storage.model.*
import skills.storage.model.SkillRelDef.RelationshipType
import skills.storage.repos.*

import static skills.storage.repos.SkillDefRepo.SkillDefPartial

@Service
@Slf4j
class GlobalBadgesService {

    @Autowired
    SkillDefRepo skillDefRepo

    @Autowired
    SkillDefWithExtraRepo skillDefWithExtraRepo

    @Autowired
    SkillRelDefRepo skillRelDefRepo

    @Autowired
    LevelDefinitionStorageService levelDefService

    @Autowired
    LevelDefRepo levelDefinitionRepository

    @Autowired
    RuleSetDefinitionScoreUpdater ruleSetDefinitionScoreUpdater

    @Autowired
    UserAchievementsAndPointsManagement userPointsManagement

    @Autowired
    RuleSetDefGraphService ruleSetDefGraphService

    @Autowired
    SkillShareDefRepo skillShareDefRepo

    @Autowired
    GlobalBadgeLevelDefRepo globalBadgeLevelDefRepo

    @Autowired
    SettingsService settingsService

    @Autowired
    SortingService sortingService

    @Autowired
    ProjDefRepo projDefRepo

    @Autowired
    AdminProjService adminProjService

    @Transactional()
    void saveBadge(String originalBadgeId, BadgeRequest badgeRequest) {
        adminProjService.saveBadge(null, originalBadgeId, badgeRequest, SkillDef.ContainerType.GlobalBadge)
    }
    @Transactional(readOnly = true)
    boolean existsByBadgeName(String subjectName) {
        return skillDefRepo.existsByProjectIdAndNameAndTypeAllIgnoreCase(null, subjectName, SkillDef.ContainerType.GlobalBadge)
    }

    @Transactional(readOnly = true)
    boolean existsByBadgeId(String skillId) {
        return skillDefRepo.existsByProjectIdAndSkillIdAllIgnoreCase(null, skillId)
    }

    @Transactional()
    void addSkillToBadge(String badgeId, String projectId, String skillId) {
        assignGraphRelationship(badgeId, SkillDef.ContainerType.GlobalBadge, projectId, skillId, RelationshipType.BadgeDependence)
    }

    @Transactional()
    void addProjectLevelToBadge(String badgeId, String projectId, Integer level) {
        SkillDefWithExtra badgeSkillDef = skillDefWithExtraRepo.findByProjectIdAndSkillIdIgnoreCaseAndType(null, badgeId, SkillDef.ContainerType.GlobalBadge)
        if (!badgeSkillDef) {
            throw new SkillException("Failed to find global badge [${badgeId}]")
        }
        ProjDef projDef = projDefRepo.findByProjectId(projectId)
        if (!projDef) {
            throw new SkillException("Failed to find project [${projectId}]", projectId)
        }
        List<LevelDef> projectLevels = levelDefinitionRepository.findAllByProjectId(projDef.id)
        projectLevels.sort({it.level})

        LevelDef toAdd = projectLevels.find { it.level == level }
        if (!toAdd) {
            throw new SkillException("Failed to find level [${level}]", projectId)
        }

        GlobalBadgeLevelDef globalBadgeLevelDef = new GlobalBadgeLevelDef(
                levelRefId: toAdd.id, level: level, projectRefId: projDef.id, projectId: projectId,
                projectName: projDef.name, badgeRefId: badgeSkillDef.id, badgeId: badgeId
        )
        adminProjService.dataIntegrityViolationExceptionHandler.handle(null) {
            globalBadgeLevelDefRepo.save(globalBadgeLevelDef)
        }
    }

    @Transactional()
    void removeProjectLevelFromBadge(String badgeId, projectId, Integer level) {
        GlobalBadgeLevelDef globalBadgeLevelDef = globalBadgeLevelDefRepo.findByBadgeIdAndProjectIdAndLevel(badgeId, projectId, level)
        if (!globalBadgeLevelDef) {
            throw new SkillException("Failed to find global badge project level for badge [${badgeId}], project [${projectId}] and level [${level}]", projectId, badgeId)
        }
        globalBadgeLevelDefRepo.delete(globalBadgeLevelDef)
    }

    @Transactional(readOnly = true)
    List<GlobalBadgeLevelRes> getGlobalBadgeLevels(String badgeId) {
        List<GlobalBadgeLevelDef> globalBadgeLevelDefs = globalBadgeLevelDefRepo.findAllByBadgeId(badgeId)
        return globalBadgeLevelDefs.collect { new GlobalBadgeLevelRes(
                badgeId: it.badgeId,
                projectId: it.projectId,
                projectName: it.projectName,
                level: it.level
        ) }
    }

    @Transactional()
    void removeSkillFromBadge(String badgeId, projectId, String skillId) {
        removeGraphRelationship(badgeId, SkillDef.ContainerType.GlobalBadge, projectId, skillId, RelationshipType.BadgeDependence)
    }
    @Transactional
    void assignGraphRelationship(String badgeSkillId, SkillDef.ContainerType skillType, String projectId,
                                 String relationshipSkillId, RelationshipType relationshipType) {
        adminProjService.assignGraphRelationship(null, badgeSkillId, skillType, projectId, relationshipSkillId, relationshipType)
    }

    @Transactional
    void removeGraphRelationship(String skillId, SkillDef.ContainerType skillType, String projectId,
                                 String relationshipSkillId, RelationshipType relationshipType){
        adminProjService.removeGraphRelationship(null, skillId, skillType, projectId, relationshipSkillId, relationshipType)
    }

    @Transactional
    void deleteBadge(String badgeId) {
        adminProjService.deleteBadge(null, badgeId, SkillDef.ContainerType.GlobalBadge)
    }

    @Transactional(readOnly = true)
    List<GlobalBadgeResult> getBadges() {
        List<SkillDefWithExtra> badges = skillDefWithExtraRepo.findAllByProjectIdAndType(null, SkillDef.ContainerType.GlobalBadge)
        List<GlobalBadgeResult> res = badges.collect { convertToBadge(it, true) }
        return res?.sort({ it.displayOrder })
    }

    @Transactional(readOnly = true)
    GlobalBadgeResult getBadge(String badgeId) {
        SkillDefWithExtra skillDef = skillDefWithExtraRepo.findByProjectIdAndSkillIdIgnoreCaseAndType(null, badgeId, SkillDef.ContainerType.GlobalBadge)
        if (skillDef) {
            return convertToBadge(skillDef, true)
        }
    }

    @Transactional
    void setBadgeDisplayOrder(String badgeId, ActionPatchRequest badgePatchRequest) {
        List<SkillDef> badges = skillDefRepo.findAllByProjectIdAndType(null,  SkillDef.ContainerType.GlobalBadge)
        adminProjService.updateDisplayOrder(badgeId, badges, badgePatchRequest)
    }

    @Transactional(readOnly = true)
    AvailableSkillsResult getAvailableSkillsForGlobalBadge(String badgeId, String query) {
        List<SkillDefPartial> allSkillDefs = skillDefRepo.findAllByTypeAndNameLike(SkillDef.ContainerType.Skill, query)
        Set<String> existingBadgeSkillIds = getSkillsForBadge(badgeId).collect { "${it.projectId}${it.skillId}" }
        List<SkillDefPartial> suggestedSkillDefs = allSkillDefs.findAll { !("${it.projectId}${it.skillId}" in existingBadgeSkillIds) }
        AvailableSkillsResult res = new AvailableSkillsResult()
        if (suggestedSkillDefs) {
            res.totalAvailable = suggestedSkillDefs.size()
            res.suggestedSkills = suggestedSkillDefs.sort().take(10).collect { adminProjService.convertToSkillDefPartialRes(it) }
        }
        return res
    }

    @Transactional(readOnly = true)
    List<SkillDefPartialRes> getSkillsForBadge(String badgeId) {
        return adminProjService.getSkillsByProjectSkillAndType(null, badgeId, SkillDef.ContainerType.GlobalBadge, RelationshipType.BadgeDependence)
    }

    @Transactional(readOnly = true)
    List<ProjectResult> getAllProjectsForBadge(String badgeId) {
        List<String> projectIdsAlreadyInBadge = globalBadgeLevelDefRepo.findAllByBadgeId(badgeId).collect { it.projectId }.unique()
        return projDefRepo.findAll().findAll { !(it.projectId in projectIdsAlreadyInBadge) }.collect { definition ->
            ProjectResult res = new ProjectResult(
                    projectId: definition.projectId, name: definition.name, totalPoints: definition.totalPoints,
                    numSubjects: definition.subjects ? definition.subjects.size() : 0,
                    displayOrder: 0,
            )
        }
    }

    @Transactional(readOnly = true)
    boolean isSkillUsedInGlobalBadge(String projectId, String skillId) {
        SkillDef skillDef = skillDefRepo.findByProjectIdAndSkillIdAndType(projectId, skillId, SkillDef.ContainerType.Skill)
        assert skillDef, "Skill [${skillId}] for project [${projectId}] does not exist"
        return isSkillUsedInGlobalBadge(skillDef)
    }

    @Transactional(readOnly = true)
    boolean isSkillUsedInGlobalBadge(SkillDef skillDef) {
        int numProjectSkillsUsedInGlobalBadge = skillRelDefRepo.getSkillUsedInGlobalBadgeCount(skillDef.skillId)
        return numProjectSkillsUsedInGlobalBadge > 0
    }

    @Transactional(readOnly = true)
    boolean isProjectLevelUsedInGlobalBadge(String projectId, Integer level) {
        int numberOfLevels = globalBadgeLevelDefRepo.countByProjectIdAndLevel(projectId, level)
        return numberOfLevels > 0
    }

    @Transactional(readOnly = true)
    boolean isProjectUsedInGlobalBadge(String projectId) {
        int numberOfLevels = globalBadgeLevelDefRepo.countByProjectId(projectId)
        if (numberOfLevels > 0) {
            return true
        }
        int numProjectSkillsUsedInGlobalBadge = skillRelDefRepo.getProjectUsedInGlobalBadgeCount(projectId)
        return numProjectSkillsUsedInGlobalBadge > 0
    }

    @Profile
    private GlobalBadgeResult convertToBadge(SkillDefWithExtra skillDef, boolean loadRequiredSkills = false) {
        GlobalBadgeResult res = new GlobalBadgeResult(
                badgeId: skillDef.skillId,
                name: skillDef.name,
                description: skillDef.description,
                displayOrder: skillDef.displayOrder,
                iconClass: skillDef.iconClass,
                startDate: skillDef.startDate,
                endDate: skillDef.endDate,
        )

        if (loadRequiredSkills) {
            List<SkillDef> dependentSkills = skillDefRepo.findChildSkillsByIdAndRelationshipType(skillDef.id, SkillRelDef.RelationshipType.BadgeDependence)
            res.requiredSkills = dependentSkills?.collect { adminProjService.convertToSkillDefRes(it) }
            res.numSkills = dependentSkills ? dependentSkills.size() : 0
            res.totalPoints = dependentSkills ? dependentSkills?.collect({ it.totalPoints })?.sum() : 0
        } else {
            res.numSkills = skillDefRepo.countChildSkillsByIdAndRelationshipType(skillDef.id, SkillRelDef.RelationshipType.BadgeDependence)
            if (res.numSkills > 0) {
                res.totalPoints = skillDefRepo.sumChildSkillsTotalPointsBySkillAndRelationshipType(skillDef.id, SkillRelDef.RelationshipType.BadgeDependence)
            } else {
                res.totalPoints = 0
            }
        }
        res.requiredProjectLevels = getGlobalBadgeLevels(skillDef.skillId)
        return res
    }

    static class AvailableSkillsResult {
        int totalAvailable = 0
        List<SkillDefPartialRes> suggestedSkills = []
    }
}
package skills.storage.repos

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import skills.storage.model.SkillDef
import skills.storage.model.SkillRelDef

interface SkillRelDefRepo extends CrudRepository<SkillRelDef, Integer> {
    List<SkillRelDef> findAllByChildAndType(SkillDef child, SkillRelDef.RelationshipType type)
    SkillRelDef findByChildAndParentAndType(SkillDef child, SkillDef parent, SkillRelDef.RelationshipType type)
    List<SkillRelDef> findAllByParentAndType(SkillDef parent, SkillRelDef.RelationshipType type)

    @Query('''SELECT 
        sd2.id as id,
        sd2.name as name, 
        sd2.skillId as skillId, 
        sd2.projectId as projectId, 
        sd2.version as version,
        sd2.pointIncrement as pointIncrement,
        sd2.pointIncrementInterval as pointIncrementInterval,
        sd2.numMaxOccurrencesIncrementInterval as numMaxOccurrencesIncrementInterval,
        sd2.totalPoints as totalPoints,
        sd2.type as skillType,
        sd2.displayOrder as displayOrder,
        sd2.created as created,
        sd2.updated as updated
        from SkillDef sd1, SkillDef sd2, SkillRelDef srd 
        where sd1 = srd.parent and sd2 = srd.child and srd.type=?3 
              and sd1.projectId=?1 and sd1.skillId=?2''')
    List<SkillDefRepo.SkillDefPartial> getChildrenPartial(String projectId, String parentSkillId, SkillRelDef.RelationshipType type)

    @Query('''SELECT sd2 
        from SkillDef sd1, SkillDef sd2, SkillRelDef srd 
        where sd1 = srd.parent and sd2 = srd.child and srd.type=?3 
              and sd1.projectId=?1 and sd1.skillId=?2''')
    List<SkillDef> getChildren(String projectId, String parentSkillId, SkillRelDef.RelationshipType type)

    @Query('''select sd1, sd2 from SkillDef sd1, SkillDef sd2, SkillRelDef srd 
        where sd1 = srd.parent and sd2 = srd.child and srd.type=?2 
              and sd1.projectId=?1''')
    List<Object[]> getGraph(String projectId, SkillRelDef.RelationshipType type)
}

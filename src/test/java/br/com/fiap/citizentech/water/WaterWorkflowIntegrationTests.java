package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.PostgresTestConfiguration;
import br.com.fiap.citizentech.activity.*;
import br.com.fiap.citizentech.croparea.domain.*;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.farm.domain.Farm;
import br.com.fiap.citizentech.farm.repository.FarmRepository;
import br.com.fiap.citizentech.user.domain.User;
import br.com.fiap.citizentech.user.repository.UserRepository;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Import(PostgresTestConfiguration.class) @Transactional @WithMockUser(roles="PRODUCER")
class WaterWorkflowIntegrationTests {
    @Autowired UserRepository users;
    @Autowired FarmRepository farms;
    @Autowired CropAreaRepository areas;
    @Autowired ActivityService activities;
    @Autowired ProfileService profiles;
    @Autowired RiskJobService jobs;
    @Test void ownershipActivitiesVersionedProfileAndSnapshot() {
        var owner=users.saveAndFlush(new User("Produtor","water-owner@example.test","hash"));
        users.saveAndFlush(new User("Outro","water-other@example.test","hash"));
        var farm=farms.saveAndFlush(new Farm(owner,"Fazenda","Campinas","SP",BigDecimal.TEN,AreaUnit.HECTARE));
        var crop=areas.saveAndFlush(new CropArea(farm,"Milho","Milho",BigDecimal.ONE,AreaUnit.HECTARE,null,null,null));
        var request=new ActivityRequest(FieldActivity.Type.IRRIGATION,Instant.now().minusSeconds(60),"Irrigação",BigDecimal.TEN,"L");
        var a=activities.create(owner.getEmail(),crop.getId(),request);
        assertEquals(a.id(),activities.get(owner.getEmail(),crop.getId(),a.id()).id());
        assertThrows(BusinessException.class,()->activities.get("water-other@example.test",crop.getId(),a.id()));
        var p=new ProfileRequest("argiloso",32.0,12.0,30.0,15.0,"DEMO",true);
        assertEquals(1,profiles.put(owner.getEmail(),crop.getId(),p).revision());
        var job=jobs.create(owner.getEmail(),crop.getId());
        assertEquals(32.0,job.getFieldCapacity());
        assertEquals(2,profiles.put(owner.getEmail(),crop.getId(),new ProfileRequest("argiloso",40.0,15.0,30.0,15.0,"DEMO",true)).revision());
        assertEquals(32.0,jobs.get(owner.getEmail(),job.getId()).getFieldCapacity());
        assertThrows(BusinessException.class,()->jobs.get("water-other@example.test",job.getId()));
        crop.archive(true); areas.flush();
        assertThrows(BusinessException.class,()->activities.create(owner.getEmail(),crop.getId(),request));
        assertThrows(BusinessException.class,()->jobs.create(owner.getEmail(),crop.getId()));
    }
}

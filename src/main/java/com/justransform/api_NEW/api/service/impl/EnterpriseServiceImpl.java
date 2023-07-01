package com.justransform.api_NEW.api.service.impl;

import com.justransform.api_NEW.model.EnterpriseData;
import com.justransform.common.vo.EnterpriseVo;
import com.justransform.common.vo.ProgramTradingPartnerVo;
import com.justransform.common.vo.ProgramVo;
import com.justransform.common.vo.UserVo;
import com.justransform.dao.EnterpriseDao;
import com.justransform.data.service.converter.ConvertorActionType;
import com.justransform.data.service.converter.EntityToVoConverter;
import com.justransform.data.service.converter.VoToEntityConverter;
import com.justransform.entity.Enterprise;
import com.justransform.entity.Program;
import com.justransform.entity.User;
import com.justransform.exception.JTServerException;
import com.justransform.services.EnterpriseService;
import com.justransform.services.ProgramService;
import com.justransform.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EnterpriseServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramServiceImpl.class);

    @Autowired
    EnterpriseService enterpriseService;

    @Autowired
    EnterpriseDao enterpriseDao;

    @Autowired
    UserService userService;

    @Autowired
    ProgramService programService;

    public EnterpriseVo getEnterprise(long enterpriseId) throws Exception {
        try {
            Enterprise enterprise = enterpriseService.getEnterprise(enterpriseId);
            EnterpriseVo enterpriseVo = EntityToVoConverter.getInstance().convertEnterpriseToEnterpriseVo(enterprise, ConvertorActionType.FETCH_JTNERENT);
            return enterpriseVo;
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    public EnterpriseVo getEnterpriseByDomain(String domain) throws Exception {
        try {
            Enterprise enterprise = enterpriseService.getEnterpriseByDomain(domain);
            EnterpriseVo enterpriseVo = EntityToVoConverter.getInstance().convertEnterpriseToEnterpriseVo(enterprise, ConvertorActionType.FETCH_B2B_ENTERPRISE_PROFILE);
            return enterpriseVo;
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    public Enterprise saveEnterprise(EnterpriseData enterpriseData) throws Exception {
        try {
            Enterprise enterprise = getEnterprise(enterpriseData);
            Enterprise createdEnterprise = enterpriseDao.createEnterprise(enterprise);
            return createdEnterprise;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), "Saving enterprise failed");
            throw new Exception(e.getMessage());
        }
    }


    public String updateEnterprise(EnterpriseData enterpriseData) throws Exception {
        try {
            Enterprise enterprise = getEnterprise(enterpriseData);

            enterpriseService.updateEnterprise(enterprise);
            return "Enterprise updated successfully";
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    public String deleteEnterprise(EnterpriseData enterpriseData) throws Exception {
        try {
            Enterprise enterprise = getEnterprise(enterpriseData);

            enterpriseDao.deleteEnterprise(enterprise);
            return "Enterprise deleted";
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    public Set<String> getTradingPartners(String domain) throws JTServerException {
        Set<String> tradingPartnersList = new HashSet<>();
        try {
            String domainExtension = domain + ".com";
            Enterprise enterprise = enterpriseService.getEnterpriseByDomain(domain);
            if (enterprise == null) {
                enterprise = enterpriseService.getEnterpriseByDomain(domainExtension);
                if (enterprise == null) {
                    throw new JTServerException("Enterprise not found");
                }
            }
            List<UserVo> TradingPartners = new ArrayList<>();
            List<User> userList = userService.getMyEnterpriseUsers(enterprise);

            List<Program> programs = userList.stream()
                    .flatMap(user -> programService.getPermissiblePrograms(user.getUserId()).stream())
                    .collect(Collectors.toList());

            List<ProgramVo> programVos = EntityToVoConverter.getInstance().convertProgramToProgramVo(programs, ConvertorActionType.FETCH_PROGRAM);

            List<ProgramTradingPartnerVo> ProgramTradingPartnersList = programVos.stream()
                    .flatMap(programVo -> programService.getAllTradingPartners(programVo).stream())
                    .collect(Collectors.toList());
            List<UserVo> invitedTps = ProgramTradingPartnersList.stream().map(ProgramTradingPartnerVo::getInvitedTradingPartner).collect(Collectors.toList());
            List<UserVo> invitingTps = ProgramTradingPartnersList.stream().map(ProgramTradingPartnerVo::getInvitingTradingPartner).collect(Collectors.toList());

            TradingPartners.addAll(invitedTps);
            TradingPartners.addAll(invitingTps);

            List<Enterprise> TradingPartnerEnterprises = new ArrayList<>();
            for(UserVo uservo : TradingPartners){
                User user = VoToEntityConverter.getInstance().convertUserVoToUser(uservo, null);
                Enterprise enterprise1 = enterpriseDao.findByUsers(user);
                TradingPartnerEnterprises.add(enterprise1);
            }

            tradingPartnersList = TradingPartnerEnterprises.stream().map(Enterprise::getDomain)
                    .filter(tpDomain -> !tpDomain.equals(domain) && !tpDomain.equals(domainExtension))
                    .map(tp -> tp.replace(".com", "")).collect(Collectors.toSet());
            return tradingPartnersList;

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        } finally {
            return tradingPartnersList;
        }
    }

    private static Enterprise getEnterprise(EnterpriseData enterpriseData) {
        Enterprise enterprise = new Enterprise();
        enterprise.setEnterpriseId(enterpriseData.getEnterpriseId());
        enterprise.setName(enterpriseData.getName());
        enterprise.setUsers(enterpriseData.getUsers());
        enterprise.setDomain(enterpriseData.getDomain());
        enterprise.setRootUser(enterpriseData.getRootUser());
        enterprise.setContacts(enterpriseData.getContact());
        enterprise.setAddresses(enterpriseData.getAddresses());
        enterprise.setWebsite(enterpriseData.getWebsite());
        enterprise.setFounded(enterpriseData.getFounded());
        enterprise.setEmployeeCount(enterpriseData.getEmployeeCount());
        enterprise.setDescription(enterpriseData.getDescription());
        enterprise.setLogoFilePath(enterpriseData.getLogoFilePath());
        enterprise.setB2bEnterpriseProfiles(enterpriseData.getB2bEnterpriseProfiles());
        enterprise.setTenant(enterpriseData.getTenant());
        enterprise.setIndustryType(enterpriseData.getIndustryType());
        enterprise.setPublicResource(enterpriseData.getPublicResource());
        enterprise.setPrivateResource(enterpriseData.getPrivateResource());
        enterprise.setJtEnterprise(enterpriseData.getJtEnterprise());
        return enterprise;
    }

}

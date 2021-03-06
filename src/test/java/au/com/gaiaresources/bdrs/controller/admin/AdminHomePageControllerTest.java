package au.com.gaiaresources.bdrs.controller.admin;


import org.junit.Test;
import org.springframework.test.web.ModelAndViewAssert;
import org.springframework.web.servlet.ModelAndView;

import au.com.gaiaresources.bdrs.controller.AbstractControllerTest;
import au.com.gaiaresources.bdrs.security.Role;

public class AdminHomePageControllerTest extends AbstractControllerTest {
    
    @Test
    public void testAdminHome() throws Exception {
        login("admin", "password", new String[] { Role.ADMIN });
        
        request.setMethod("GET");
        request.setRequestURI("/admin/home.htm");

        ModelAndView mv = handle(request, response);
        ModelAndViewAssert.assertViewName(mv, "adminHome");
        ModelAndViewAssert.assertModelAttributeAvailable(mv, "recordCount");
    }
}
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
* @author Alexey V. Varlamov
* @author Peter Firmstone.
* @version $Revision$
*/

package org.apache.river.imp.security.policy.util;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.acl.Group;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import sun.security.util.SecurityConstants;


/**
 * This class represents an elementary block of a security policy. It associates
 * a CodeSource of an executable code, Principals allowed to execute the code,
 * and a set of granted Permissions.
 * 
 * Immutable
 * 
 * 
 */
public final class PolicyEntry {
    // Creation Context
    public static final int CLASSLOADER = 0;
    public static final int CODESOURCE = 1;
    public static final int PROTECTIONDOMAIN = 2;
    public static final int CODESOURCE_CERTS = 3;
    // Store CodeSource
    private final CodeSource cs;
    private final List<Certificate> certs; //TODO certs comparison etc.
    private final WeakReference<ProtectionDomain> domain;
    private final boolean hasDomain;
    
    // Array of principals 
    private final List<Principal> principals;

    // Permissions collection
    private final Collection<Permission> permissions;
    
    private transient final int hashcode;
    
    private final int context;
    
    /**
     * Constructor with initialization parameters. Passed collections are not
     * referenced directly, but copied.  This constructor is for
     * grants by CodeSource either read from files or granted at runtime.
     */
    public PolicyEntry(Certificate[] codeSourceCertificates, Collection<? extends Principal> prs,
            Collection<? extends Permission> permissions) {
        if ( prs == null || prs.isEmpty()) {
            this.principals = Collections.emptyList(); // Java 1.5
        }else{
            this.principals = new ArrayList<Principal>(prs.size());
            this.principals.addAll(prs);
                }          
        if (permissions == null || permissions.isEmpty()) {
            this.permissions = Collections.emptySet(); // Java 1.5
        }else{
            this.permissions = new HashSet<Permission>(permissions.size());
            this.permissions.addAll(permissions);
        }
        certs = Arrays.asList(codeSourceCertificates.clone());
        cs = null;
        domain = null;
        hasDomain = false;
        context = 3;
        /* Effectively immutable, this will make any hash this is contained in perform.
         * May need to consider Serializable for this class yet, we'll see.
         */ 
        hashcode = calculateHashCode();
    }
    
    /**
     * Constructor with initialization parameters. Passed collections are not
     * referenced directly, but copied.  This constructor is for
     * grants by CodeSource either read from files or granted at runtime.
     */
    public PolicyEntry(CodeSource cs, Collection<? extends Principal> prs,
            Collection<? extends Permission> permissions) {
        this.cs = (cs != null) ? normalizeCodeSource(cs) : null;
        if ( prs == null || prs.isEmpty()) {
            this.principals = Collections.emptyList(); // Java 1.5
        }else{
            this.principals = new ArrayList<Principal>(prs.size());
            this.principals.addAll(prs);
                }          
        if (permissions == null || permissions.isEmpty()) {
            this.permissions = Collections.emptySet(); // Java 1.5
        }else{
            this.permissions = new HashSet<Permission>(permissions.size());
            this.permissions.addAll(permissions);
        }
        domain = null;
        hasDomain = false;
        context = 1;
        certs = null;
        /* Effectively immutable, this will make any hash this is contained in perform.
         * May need to consider Serializable for this class yet, we'll see.
         */ 
        hashcode = calculateHashCode();
    }

    
    /**
     * Runtime Permission Grants, collections are copied.
     * @param pd ProtectionDomain
     * @param context int constant for the context of the PolicyEntry grant.
     * @param prs principals
     * @param permissions
     */
    public PolicyEntry(ProtectionDomain pd, int context, Collection<? extends Principal> prs,
            Collection<? extends Permission> permissions ){
        if ( context < 0 ){
            throw new IllegalStateException("context must be >= 0");
        }
        if ( context > 2 ){
            throw new IllegalStateException("context must be <= 2");
        }
        this.context = context;
        if ( prs == null || prs.isEmpty()) {
            this.principals = Collections.emptyList(); // Java 1.5
        }else{
            this.principals = new ArrayList<Principal>(prs.size());
            this.principals.addAll(prs);
                }          
        if (permissions == null || permissions.isEmpty()) {
            this.permissions = Collections.emptySet(); // Java 1.5
        }else{
            this.permissions = new HashSet<Permission>(permissions.size());
            this.permissions.addAll(permissions);
        }
        /* Effectively immutable, this will make any hash this is contained in perform.
         * May need to consider Serializable for this class yet, we'll see.
         */
        if (pd ==  null){
            hasDomain = false;
            domain = null;
            cs = null;
        } else if (context != 1) {
            hasDomain = true;
            domain = new WeakReference<ProtectionDomain>(pd);
            cs = pd.getCodeSource();
        } else {
            // context == 1 and pd != null
            hasDomain = false;
            domain = null;
            cs = pd.getCodeSource();
        }
        certs = null;
        hashcode = calculateHashCode();
    }
    
    public PolicyEntry(PolicyEntry pe, 
            Collection<? extends Permission> permissions){
        this.cs = pe.cs;
        this.hasDomain = pe.hasDomain;
        this.domain = pe.domain;
        this.principals = pe.principals;
        this.context = pe.context;
        this.certs = pe.certs;
        if (permissions == null || permissions.isEmpty()) {
            this.permissions = Collections.emptySet(); // Java 1.5
        }else{
            this.permissions = new HashSet<Permission>(permissions.size());
            this.permissions.addAll(permissions);
        }
        hashcode = calculateHashCode();
    }
    
    public ProtectionDomain getProtectionDomain(){
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SecurityConstants.GET_PD_PERMISSION);
        }
        if (hasDomain){
            return domain.get();
        }
        return null;
    }
    
    /**
     * This is a multi comparison 
     * @param pd ProtectionDomain
     * @return
     */
    public boolean implies(ProtectionDomain pd){
        CodeSource cs = null;
        ClassLoader cl = null;
        Principal[] pals = null;
        if (pd != null){
            cs = pd.getCodeSource();
            cl = pd.getClassLoader();
            pals = pd.getPrincipals();
        }
        if (context == 0){
            // ClassLoader and Principal comparison
            if (impliesClassLoader(cl) && impliesPrincipals(pals)){
                return true;
            }
        } else if (context == 2){
            // ProtectionDomain comparison
            if (impliesProtectionDomain(pd)){
                return true;
            }
        } else if (context == 1){
            // CodeSource and Principal comparison
            if (impliesCodeSource(cs) && impliesPrincipals(pals)) {
                return true;
            }       
        } else if (context == 3){
            // Certificate and Principal comparison
            if (impliesCertificates(cs.getCertificates()) && impliesPrincipals(pals)) {
                return true;
            }   
        }
        return false;
    }
    
    /**
     * Checks if passed ClassLoader matches this PolicyEntry. Null ProtectionDomain of
     * PolicyEntry implies any ClassLoader, unless the ProtectionDomain has
     * become garbage collected, in which case it will be false;
     * 
     * This implies is public to assist in removal of Permission grants
     * from a ClassLoader space.  In other words it ignores context.
     * 
     * It isn't very smart, it misses other grants, so isn't a guarantee that
     * a permission grant won't apply to a particluar ClassLoader, in the
     * case of Principals and Certificate grants.
     * 
     * non-null ProtectionDomain's are
     * compared with equals();
     */
    public boolean impliesClassLoader(ClassLoader cl) {
        if (hasDomain == false) return true;
        if (cl == null) return false;       
        if (domain.get() == null ) return false; // hasDomain already true
        return domain.get().getClassLoader().equals(cl); // pd not null
    }
    
    /**
     * Checks if passed ProtectionDomain matches this PolicyEntry. Null ProtectionDomain of
     * PolicyEntry implies any ProtectionDomain; non-null ProtectionDomain's are
     * compared with equals();
     */
    private boolean impliesProtectionDomain(ProtectionDomain pd) {
        if (hasDomain == false) return true;
        if (pd == null) return false;       
        if (domain.get() == null ) return false; // hasDomain already true
        return pd.equals(domain.get()); // pd not null
    }

    /**
     * Checks if passed CodeSource matches this PolicyEntry. Null CodeSource of
     * PolicyEntry implies any CodeSource; non-null CodeSource forwards to its
     * imply() method.
     */
    public boolean impliesCodeSource(CodeSource codeSource) {
        if (cs == null) return true;
        if (codeSource == null) return false;       
        return cs.implies(normalizeCodeSource(codeSource));
    }

    private CodeSource normalizeCodeSource(CodeSource codeSource) {
        URL codeSourceURL = PolicyUtils.normalizeURL(codeSource.getLocation());
        CodeSource result = codeSource;

        if (codeSourceURL != codeSource.getLocation()) {
            // URL was normalized - recreate codeSource with new URL
            CodeSigner[] signers = codeSource.getCodeSigners();
            if (signers == null) {
                result = new CodeSource(codeSourceURL, codeSource
                        .getCertificates());
            } else {
                result = new CodeSource(codeSourceURL, signers);
            }
        }
        return result;
    }
    
    private boolean impliesCertificates( Certificate[] signers){
        if ( certs.isEmpty()) return true;
        if ( signers == null || signers.length == 0 ) return false;
        List<Certificate> certificates = Arrays.asList(signers);
        return certificates.containsAll(certs);
    }

    /**
     * Checks if specified Principals match this PolicyEntry. Null or empty set
     * of Principals of PolicyEntry implies any Principals; otherwise specified
     * array must contain all Principals of this PolicyEntry.
     * @param prs 
     * @return
     */
    public boolean impliesPrincipals(Principal[] prs) {
        if ( principals.isEmpty()) return true;
        if ( prs == null || prs.length == 0 ) return false;
        // PolicyEntry Principals match if equal or if they are Groups and
        // the Principals being tested are their members.  Every Principal
        // in this PolicyEntry must have a match.
        List<Principal> princp = Arrays.asList(prs);
        int matches = 0;
        Iterator<Principal> principalItr = principals.iterator();
        while (principalItr.hasNext()){
            Principal entrypal = principalItr.next();
            Group g = null;
            if ( entrypal instanceof Group ){
                g = (Group) entrypal;
            }
            Iterator<Principal> p = princp.iterator();
            // The first match breaks out of internal loop.
            while (p.hasNext()){
                Principal implied = p.next();
                if (entrypal.equals(implied)) {
                    matches++;
                    break;
                }
                if ( g != null && g.isMember(implied) ) {
                    matches++;
                    break;
                }
            }  
        }
        if (matches == principals.size()) return true;
        return false;
    }
   
    /**
     * Returns unmodifiable collection of permissions defined by this
     * PolicyEntry, may be <code>null</code>.
     */
    public Collection<Permission> getPermissions() {
//        if (permissions.isEmpty()) return null; // not sure if this is good needs further investigation
        return Collections.unmodifiableCollection(permissions);
    }
    
    /**
     * Returns true if this PolicyEntry defines no Permissions, false otherwise.
     */
    public boolean isVoid() {
        if (permissions.size() == 0 ) return true;
        if (hasDomain == true && domain.get() == null) return true;
        return false;
    }
    
    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if ( !(o instanceof PolicyEntry)) return false;
        PolicyEntry pe = (PolicyEntry) o;
        if ( hasDomain == pe.hasDomain
                && (domain == pe.domain || domain.get().equals(pe.domain.get()))
                && (cs == pe.cs || cs.equals(pe.cs)) 
                && principals.equals(pe.principals) 
                && permissions.equals(pe.permissions) ) 
            return true;
        return false;
    }
    
    @Override
    public int hashCode(){
        return hashcode;        
    }
    
    /* Effectively immutable, this will make any hash this is contained in perform.
     * May need to consider Serializable for this class yet, we'll see.
     */
    private int calculateHashCode(){
        int pdHash = 0;
        int codeHash = 0;      
        if (hasDomain){
            ProtectionDomain d = domain.get();
            if (d != null){
                pdHash = d.hashCode();
            }
        }
        if (cs != null){
            codeHash = cs.hashCode();
        }        
        return (
                pdHash + codeHash
                + context * 31
                + principals.hashCode() 
                + permissions.hashCode());
    }
    
    @Override
    public String toString(){
        String domainString = ( domain == null || domain.get() == null) 
                ?  "" : domain.get().toString();
        String csString = (cs == null)?  "": cs.toString();
        String prinStr = principals.toString();
        String permStr = permissions.toString();
        int size = domainString.length()+ csString.length() 
                + prinStr.length() + permStr.length();
        StringBuilder sb = new StringBuilder(size);
        sb.append(domainString).append(csString).append(prinStr).append(permStr);
        return sb.toString();
    }
}

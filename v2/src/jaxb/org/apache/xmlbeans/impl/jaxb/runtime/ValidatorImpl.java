/*
* The Apache Software License, Version 1.1
*
*
* Copyright (c) 2003 The Apache Software Foundation.  All rights 
* reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
* 1. Redistributions of source code must retain the above copyright
*    notice, this list of conditions and the following disclaimer. 
*
* 2. Redistributions in binary form must reproduce the above copyright
*    notice, this list of conditions and the following disclaimer in
*    the documentation and/or other materials provided with the
*    distribution.
*
* 3. The end-user documentation included with the redistribution,
*    if any, must include the following acknowledgment:  
*       "This product includes software developed by the
*        Apache Software Foundation (http://www.apache.org/)."
*    Alternately, this acknowledgment may appear in the software itself,
*    if and wherever such third-party acknowledgments normally appear.
*
* 4. The names "Apache" and "Apache Software Foundation" must 
*    not be used to endorse or promote products derived from this
*    software without prior written permission. For written 
*    permission, please contact apache@apache.org.
*
* 5. Products derived from this software may not be called "Apache 
*    XMLBeans", nor may "Apache" appear in their name, without prior 
*    written permission of the Apache Software Foundation.
*
* THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
* OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
* ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
* USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
* OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
* SUCH DAMAGE.
* ====================================================================
*
* This software consists of voluntary contributions made by many
* individuals on behalf of the Apache Software Foundation and was
* originally based on software copyright (c) 2000-2003 BEA Systems 
* Inc., <http://www.bea.com/>. For more information on the Apache Software
* Foundation, please see <http://www.apache.org/>.
*/

package org.apache.xmlbeans.impl.jaxb.runtime;

import java.util.List;
import java.util.ArrayList;

import javax.xml.bind.Validator;
import javax.xml.bind.ValidationException;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.PropertyException;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import javax.xml.bind.helpers.ValidationEventImpl;

import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlError;

public class ValidatorImpl implements Validator 
{
    ValidationEventHandler _handler = new DefaultValidationEventHandler();

    public void setEventHandler(ValidationEventHandler handler) 
    {
        _handler = handler;
    }

    public ValidationEventHandler getEventHandler() 
    {
        return _handler;
    }

    public Object getProperty(String property) throws PropertyException
    {
        throw new PropertyException(property);
    }

    public void setProperty(String property, Object value) throws PropertyException
    {
        throw new PropertyException(property,  value);
    }

    public boolean validate(Object o) throws ValidationException
    {
        if (o == null)
            throw new IllegalArgumentException();

        if (! ( o instanceof XmlObject))
            throw new ValidationException("Cannot validate tree rooted at subrootObj.");

        return validateImpl((XmlObject)o, _handler);
    }

    public boolean validateRoot(Object o) throws ValidationException
    {
        return validate(o);
    }

    static boolean validateImpl(XmlObject o, ValidationEventHandler handler)
        throws ValidationException
    {

        assert o != null && handler != null;

        final List l = new ArrayList();
        final XmlOptions opts = new XmlOptions().setErrorListener(l);

        final boolean valid = o.validate(opts);

        // convert to ValidationEvent and dispatch
        for (int i = 0 ; i < l.size() ; i++)
        {
            XmlError xe = (XmlError)l.get(i);

            int severity = xe.getSeverity() == XmlError.SEVERITY_ERROR ? 
                ValidationEvent.ERROR :
                ValidationEvent.WARNING;

            ValidationEvent ve = new ValidationEventImpl(severity, xe.getMessage(), 
                ValidationLocatorEventImpl.create(xe));

            try 
            {
                if (! handler.handleEvent(ve))
                    throw new ValidationException(ve.getMessage());
            }
            catch (RuntimeException e)
            {
                throw new ValidationException(e);
            }
        }

        return valid;
    }

}
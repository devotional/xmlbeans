package org.apache.xmlbeans.impl.marshal;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.binding.bts.BindingLoader;
import org.apache.xmlbeans.impl.binding.bts.BuiltinBindingType;

final class BuiltinRuntimeBindingType
    extends RuntimeBindingType
{
    BuiltinRuntimeBindingType(BuiltinBindingType type,
                              TypeConverter converter)
        throws XmlException
    {
        super(type, converter, converter);
        assert converter != null;
    }

    void accept(RuntimeTypeVisitor visitor)
        throws XmlException
    {
        visitor.visit(this);
    }

    public void initialize(RuntimeBindingTypeTable typeTable,
                           BindingLoader bindingLoader)
        throws XmlException
    {
    }

    boolean hasElementChildren()
    {
        return false;
    }


}
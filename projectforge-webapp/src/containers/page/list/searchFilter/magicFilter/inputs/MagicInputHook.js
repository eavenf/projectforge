import React from 'react';
import MagicInputNotImplemented from './MagicInputNotImplemented';
import MagicSelectInput from './MagicSelectInput';
import MagicStringInput from './MagicStringInput';
import MagicTimeStampInput from './MagicTimeStampInput';

const useMagicInput = (type) => {
    const [inputTag, setInputTag] = React.useState(() => MagicInputNotImplemented);

    React.useEffect(() => {
        let tag;

        switch (type) {
            case 'STRING':
                tag = MagicStringInput;
                break;
            case 'SELECT':
                tag = MagicSelectInput;
                break;
            case 'TIME_STAMP':
                tag = MagicTimeStampInput;
                break;
            default:
                tag = MagicInputNotImplemented;
                break;
        }

        if (tag !== inputTag) {
            // Important to use a state function to prevent the tag to be called
            setInputTag(() => tag);
        }
    }, [type]);

    return inputTag;
};

export default useMagicInput;

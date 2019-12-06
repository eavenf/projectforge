import classNames from 'classnames';
import PropTypes from 'prop-types';
import React from 'react';
import { useClickOutsideHandler } from '../../../utilities/hooks';
import style from './Popper.module.scss';

function AdvancedPopper(
    {
        additionalClassName,
        basic,
        children,
        className,
        contentClassName,
        isOpen,
        setIsOpen,
    },
) {
    const reference = React.useRef(null);
    const basicReference = React.useRef(null);
    const [basicHeight, setBasicHeight] = React.useState(0);
    const [basicWidth, setBasicWidth] = React.useState(0);

    useClickOutsideHandler(reference, setIsOpen, isOpen);

    React.useLayoutEffect(
        () => {
            setBasicHeight(basicReference.current.clientHeight);
            setBasicWidth(basicReference.current.clientWidth);
        },
        [
            basicReference.current && basicReference.current.clientHeight,
            basicReference.current && basicReference.current.clientWidth,
        ],
    );

    return (
        <div
            ref={reference}
            className={classNames(
                style.advancedPopperContainer,
                { [style.isOpen]: isOpen },
                className,
            )}
        >
            <div
                className={classNames(style.content, contentClassName)}
                role="menu"
                ref={basicReference}
                tabIndex={0}
                onFocus={() => setIsOpen(true)}
                onClick={() => setIsOpen(true)}
                onKeyDown={() => setIsOpen(true)}
            >
                {basic}
            </div>
            <div
                className={classNames(style.additional, additionalClassName)}
                style={{
                    top: basicHeight + 10,
                    minWidth: basicWidth + 2,
                }}
            >
                {children}
            </div>
        </div>
    );
}

AdvancedPopper.propTypes = {
    basic: PropTypes.node.isRequired,
    children: PropTypes.node.isRequired,
    setIsOpen: PropTypes.func.isRequired,
    additionalClassName: PropTypes.string,
    className: PropTypes.string,
    contentClassName: PropTypes.string,
    isOpen: PropTypes.bool,
};

AdvancedPopper.defaultProps = {
    additionalClassName: undefined,
    className: undefined,
    contentClassName: undefined,
    isOpen: false,
};

export default AdvancedPopper;

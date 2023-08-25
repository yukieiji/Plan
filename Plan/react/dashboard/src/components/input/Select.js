import React from 'react';

const MultiSelect = ({options, selectedIndex, setSelectedIndex}) => {
    const handleChange = (event) => {
        const renderedOptions = Object.values(event.target.selectedOptions)
            .map(htmlElement => htmlElement.text)
            .map(option => options.indexOf(option));
        setSelectedIndex(renderedOptions[0]);
    }

    return (
        <select className="form-control"
                onChange={handleChange}>
            {options.map((option, i) => {
                return (
                    <option key={JSON.stringify(option)} value={selectedIndex === i}
                            selected={selectedIndex === i}>{option}</option>
                )
            })}
        </select>
    )
};

export default MultiSelect